package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.settings.ApiKey;
import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Clearing the database end to end with a scanned library.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class DatabaseResetIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String MIGRATION_HISTORY = "flyway_schema_history";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private ApiKeyService apiKeyService;

    @TempDir
    Path library;

    private Path movieFile;

    @BeforeEach
    void givenLibraryAndSettings() throws IOException {
        Path heat = Files.createDirectories(library.resolve("filme/Heat"));
        movieFile = Files.writeString(heat.resolve("Heat.mkv"), "");
        Files.writeString(heat.resolve("Heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><genre>Krimi</genre><set><name>Saga</name></set>
                <actor><name>Al Pacino</name></actor></movie>""");
        Path alf = Files.createDirectories(library.resolve("serien/ALF"));
        Files.writeString(alf.resolve("tvshow.nfo"), "<tvshow><title>ALF</title><genre>Komödie</genre></tvshow>");
        Files.writeString(alf.resolve("ALF - S01E01.mkv"), "");
        scan("Filme", library.resolve("filme"), "MOVIE");
        scan("Serien", library.resolve("serien"), "TV_SHOW");
        KmmSettings defaults = KmmSettings.DEFAULTS;
        settingsService.update(new KmmSettings("fr-FR", defaults.artworkLanguage(), defaults.videoExtensions(),
                defaults.excludedPaths(), defaults.proxy(), defaults.tv(), defaults.mediaInfo(), defaults.rename(),
                defaults.movieSets()));
        apiKeyService.update(Map.of(ApiKey.values()[0], "test-key-12345678"));
    }

    @AfterEach
    void cleanUp() {
        clear(false);
    }

    @Test
    void knowsEveryTableOfTheSchema() {
        List<String> tables = jdbcClient.sql("""
                SELECT LOWER(table_name) FROM information_schema.tables
                WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'""").query(String.class).list();

        assertThat(tables).filteredOn(table -> !MIGRATION_HISTORY.equals(table))
                .containsExactlyInAnyOrderElementsOf(allTables());
    }

    @Test
    void clearsTheLibraryAndKeepsSettingsAndFiles() {
        assertThat(count("movie")).isEqualTo(1);
        assertThat(count("episode")).isEqualTo(1);

        assertThat(clear(true)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(DatabaseResetService.LIBRARY_TABLES).allSatisfy(table -> assertThat(count(table)).isZero());
        assertThat(settingsService.current().metadataLanguage()).isEqualTo("fr-FR");
        assertThat(apiKeyService.view().getFirst().set()).isTrue();
        assertThat(movieFile).exists();
        assertThat(movieFile.resolveSibling("Heat.nfo")).exists();
    }

    @Test
    void clearsSettingsAndApiKeysOnRequest() {
        assertThat(clear(false)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(allTables()).allSatisfy(table -> assertThat(count(table)).isZero());
        assertThat(settingsService.current().metadataLanguage()).isEqualTo(KmmSettings.DEFAULTS.metadataLanguage());
        assertThat(apiKeyService.view().getFirst().set()).isFalse();
    }

    @Test
    void keepsTheMigrationHistorySoTheSchemaStaysUsable() {
        clear(false);

        assertThat(count(MIGRATION_HISTORY)).isPositive();
        scan("Filme", library.resolve("filme"), "MOVIE");
        assertThat(count("movie")).isEqualTo(1);
    }

    /** Retries while the scan and its follow-up jobs are still running and block the clearing. */
    private MvcTestResult clear(boolean keepSettings) {
        return await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> mockMvc.post().uri(DatabaseResetController.CLEAR_PATH).with(csrf()).contentType(JSON)
                        .content("{\"keepSettings\": %s}".formatted(keepSettings)).exchange(),
                        result -> result.getResponse().getStatus() != HttpStatus.CONFLICT.value());
    }

    private static List<String> allTables() {
        return Stream.concat(DatabaseResetService.LIBRARY_TABLES.stream(),
                Stream.of(DatabaseResetService.SETTINGS_TABLE)).toList();
    }

    private int count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + (MIGRATION_HISTORY.equals(table) ? "\"" + table + "\"" : table))
                .query(Integer.class).single();
    }

    private void scan(String name, Path path, String mediaType) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "path", path.toString(),
                        "mediaType", mediaType)))
                .exchange();
        assertThat(source).hasStatus(HttpStatus.CREATED);
        String jobId = read(mockMvc.post().uri(LibraryScanController.SCAN_PATH, read(source).get("id").asLong())
                .with(csrf()).exchange()).get("id").asString();
        await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
