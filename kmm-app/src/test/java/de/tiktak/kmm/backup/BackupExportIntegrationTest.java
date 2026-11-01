package de.tiktak.kmm.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.settings.ApiKey;
import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.system.DatabaseResetService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Export der Sicherung Ende zu Ende.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class BackupExportIntegrationTest {

    private static final String API_KEY = "api-key-streng-geheim";
    private static final String KODI_PASSWORD = "kodi-streng-geheim";
    private static final Instant ADDED_AT = Instant.parse("2026-10-30T12:34:56.123456Z");
    private static final String MIGRATION_HISTORY = "flyway_schema_history";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private DatabaseResetService databaseResetService;

    @Autowired
    private JobService jobService;

    @Value("${kmm.home}")
    private Path home;

    @BeforeEach
    void givenData() {
        apiKeyService.update(Map.of(ApiKey.TMDB_API_KEY, API_KEY));
        jdbcClient.sql("""
                INSERT INTO media_source (id, name, path, media_type, created_at)
                VALUES (1, 'Filme', '/medien/filme', 'MOVIE', TIMESTAMP WITH TIME ZONE '2026-10-30 12:00:00+00')""")
                .update();
        jdbcClient.sql("""
                INSERT INTO movie (id, source_id, file_path, file_path_hash, title, disc_format, part_count, added_at)
                VALUES (1, 1, '/medien/filme/Heat/Heat.mkv', 'hash', 'Heat', 'FILE', 1,
                        TIMESTAMP WITH TIME ZONE '2026-10-30 12:34:56.123456+00')""").update();
        jdbcClient.sql("INSERT INTO movie_rating (movie_id, rating_order, source, rating_value, default_rating) "
                + "VALUES (1, 0, 'imdb', 8.3, TRUE)").update();
        jdbcClient.sql("""
                INSERT INTO kodi_host (id, name, address, port, username, password, realtime_sync)
                VALUES (1, 'Wohnzimmer', 'kodi', 8080, 'kodi', ?, TRUE)""").param(KODI_PASSWORD).update();
    }

    @AfterEach
    void cleanUp() {
        databaseResetService.clear(false);
    }

    @Test
    void knowsEveryTableOfTheSchema() {
        List<String> tables = jdbcClient.sql("""
                SELECT LOWER(table_name) FROM information_schema.tables
                WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'""").query(String.class).list();

        assertThat(BackupTables.ALL).extracting(BackupTable::name)
                .containsExactlyInAnyOrderElementsOf(tables.stream()
                        .filter(table -> !MIGRATION_HISTORY.equals(table)).toList());
    }

    @Test
    void listsEveryTableBehindTheTablesItRefersTo() {
        List<String> names = BackupTables.ALL.stream().map(BackupTable::name).toList();

        assertThat(BackupTables.ALL).allSatisfy(table -> assertThat(table.parents().values())
                .allSatisfy(parent -> assertThat(names.indexOf(parent)).isLessThan(names.indexOf(table.name()))));
    }

    @Test
    void writesHeaderAndValuesOfEveryType() throws IOException {
        MvcTestResult result = export(false);

        assertThat(result).hasStatusOk().hasContentType("application/gzip");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .startsWith("attachment; filename=\"kmm-sicherung-").endsWith(".json.gz\"");
        JsonNode backup = read(result);
        assertThat(backup.get(BackupFormat.FORMAT).asInt()).isEqualTo(BackupFormat.VERSION);
        assertThat(backup.get(BackupFormat.SCHEMA_VERSION).asString()).isEqualTo("21");
        assertThat(backup.get(BackupFormat.COUNTS).get(BackupTables.MOVIE).asInt()).isEqualTo(1);
        JsonNode movie = backup.get(BackupFormat.TABLES).get(BackupTables.MOVIE).get(0);
        assertThat(movie.get("title").asString()).isEqualTo("Heat");
        assertThat(movie.get("added_at").asString()).isEqualTo(ADDED_AT.toString());
        assertThat(movie.get("nfo_read").isBoolean()).isTrue();
        assertThat(movie.get("premiered").isNull()).isTrue();
        assertThat(backup.get(BackupFormat.TABLES).get("movie_rating").get(0).get("rating_value").asDouble())
                .isEqualTo(8.3);
        assertThat(backup.get(BackupFormat.TABLES).propertyNames())
                .containsExactlyElementsOf(BackupTables.ALL.stream().map(BackupTable::name).toList());
    }

    @Test
    void leavesCredentialsOutByDefault() throws IOException {
        byte[] content = gunzip(export(false));

        assertThat(new String(content, StandardCharsets.UTF_8)).doesNotContain(API_KEY, KODI_PASSWORD);
        JsonNode backup = objectMapper.readTree(content);
        assertThat(backup.get(BackupFormat.CREDENTIALS).asBoolean()).isFalse();
        assertThat(backup.get(BackupFormat.TABLES).get(BackupTables.KODI_HOST).get(0).get("name").asString())
                .isEqualTo("Wohnzimmer");
        assertThat(backup.get(BackupFormat.COUNTS).get(BackupTables.APP_SETTING).asInt())
                .isEqualTo(backup.get(BackupFormat.TABLES).get(BackupTables.APP_SETTING).size());
    }

    @Test
    void includesCredentialsOnRequest() throws IOException {
        String content = new String(gunzip(export(true)), StandardCharsets.UTF_8);

        assertThat(content).contains(API_KEY, KODI_PASSWORD);
    }

    @Test
    void deletesTheTemporaryFileAfterSending() throws IOException {
        export(true);

        try (var files = Files.list(home.resolve(BackupFiles.DIRECTORY_NAME))) {
            assertThat(files.filter(file -> file.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }
    }

    @Test
    void refusesWhileAJobIsRunningAndLeavesNoFile() throws IOException {
        CountDownLatch finish = new CountDownLatch(1);
        jobService.submit("test", "Testjob", context -> {
            try {
                finish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "fertig";
        });
        try {
            assertThat(export(true)).hasStatus(HttpStatus.CONFLICT);
            try (var files = Files.list(home.resolve(BackupFiles.DIRECTORY_NAME))) {
                assertThat(files.filter(file -> file.getFileName().toString().endsWith(".tmp"))).isEmpty();
            }
        } finally {
            finish.countDown();
        }
        await().until(() -> jobService.findAll().stream()
                .noneMatch(job -> job.status() == JobStatus.RUNNING));
    }

    private MvcTestResult export(boolean credentials) {
        return mockMvc.post().uri(BackupController.EXPORT_PATH).param("credentials", String.valueOf(credentials))
                .with(csrf()).exchange();
    }

    private JsonNode read(MvcTestResult result) throws IOException {
        return objectMapper.readTree(gunzip(result));
    }

    private static byte[] gunzip(MvcTestResult result) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            return input.readAllBytes();
        }
    }
}
