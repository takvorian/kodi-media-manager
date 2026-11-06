package de.tiktak.kmm.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobAlreadyRunningException;
import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.settings.ApiKey;
import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceController;
import de.tiktak.kmm.system.DatabaseResetService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Backup import end to end: round trip over a really scanned library, both import modes, paths, credentials and
 * unusable files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class BackupImportIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String API_KEY = "api-key-streng-geheim";
    private static final String KODI_PASSWORD = "kodi-streng-geheim";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private DatabaseResetService databaseResetService;

    @Value("${kmm.home}")
    private Path home;

    @TempDir
    Path library;

    @BeforeEach
    void givenLibrary() throws IOException {
        Path heat = Files.createDirectories(library.resolve("filme/Heat"));
        Files.writeString(heat.resolve("Heat.mkv"), "");
        Files.writeString(heat.resolve("Heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><plot>Überfall</plot><genre>Krimi</genre>
                <set><name>Saga</name></set><actor><name>Al Pacino</name></actor>
                <ratings><rating name="imdb" max="10" default="true"><value>8.3</value></rating></ratings></movie>""");
        Path alf = Files.createDirectories(library.resolve("serien/ALF"));
        Files.writeString(alf.resolve("tvshow.nfo"), "<tvshow><title>ALF</title><genre>Komödie</genre></tvshow>");
        Files.writeString(alf.resolve("ALF - S01E01.mkv"), "");
        Files.writeString(alf.resolve("ALF - S01E01.nfo"), "<episodedetails><title>Pilot</title><season>1</season>"
                + "<episode>1</episode><actor><name>Paul Fusco</name></actor></episodedetails>");
        scan("Filme", library.resolve("filme"), "MOVIE");
        scan("Serien", library.resolve("serien"), "TV_SHOW");
        apiKeyService.update(Map.of(ApiKey.TMDB_API_KEY, API_KEY));
        jdbcClient.sql("""
                INSERT INTO kodi_host (name, address, port, username, password, realtime_sync)
                VALUES ('Wohnzimmer', 'kodi', 8080, 'kodi', ?, TRUE)""").param(KODI_PASSWORD).update();
        jdbcClient.sql("""
                INSERT INTO kodi_host_path_mapping (kodi_host_id, mapping_order, local_path, kodi_path)
                SELECT id, 0, ?, 'smb://nas/filme/' FROM kodi_host""").param(library.resolve("filme").toString()).update();
    }

    @AfterEach
    void cleanUp() {
        await().atMost(TIMEOUT).pollInSameThread().until(() -> {
            try {
                databaseResetService.clear(false);
                return true;
            } catch (JobAlreadyRunningException e) {
                return false;
            }
        });
        mockMvc.delete().uri(BackupController.UPLOAD_PATH).with(csrf()).exchange();
    }

    @Test
    void replaceRestoresTheExactStateIncludingTimestampsAndLists() throws IOException {
        byte[] backup = export(true);
        JsonNode before = comparable(backup);
        jdbcClient.sql("UPDATE movie SET title = 'Geändert'").update();
        jdbcClient.sql("DELETE FROM movie_credit").update();
        jdbcClient.sql("INSERT INTO saved_filter (media_type, name, definition) VALUES ('MOVIE', 'Weg', '{}')").update();

        JsonNode report = runImport(backup, "REPLACE", List.of(), false);

        assertThat(report.get("mode").asString()).isEqualTo("REPLACE");
        assertThat(comparable(export(true))).isEqualTo(before);
    }

    @Test
    void aScanAfterTheImportFindsNothingToUpdate() throws IOException {
        byte[] backup = export(true);
        runImport(backup, "REPLACE", List.of(), false);

        JsonNode result = rescan("MOVIE");

        assertThat(result.get("updated").asInt()).isZero();
        assertThat(result.get("added").asInt()).isZero();
        assertThat(result.get("removed").asInt()).isZero();
    }

    @Test
    void mapsPathsAndHashesSoAScanRecognisesEveryEntry() throws IOException {
        Path moved = library.resolveSibling(library.getFileName() + "-umgezogen");
        byte[] backup = export(true);
        Files.move(library.resolve("filme"), Files.createDirectories(moved).resolve("filme"));
        Files.move(library.resolve("serien"), moved.resolve("serien"));
        try {
            JsonNode report = runImport(backup, "REPLACE", List.of(Map.of("from", library.toString(), "to",
                    moved.toString())), false);

            assertThat(report.get("warnings")).isEmpty();
            assertThat(report.get("unmappedPaths").asInt()).isZero();
            assertThat(path("media_source", "path")).startsWith(moved.toString());
            assertThat(path("movie", "file_path")).startsWith(moved.toString());
            assertThat(path("tv_show", "directory_path")).startsWith(moved.toString());
            assertThat(path("episode", "file_path")).startsWith(moved.toString());
            assertThat(path("kodi_host_path_mapping", "local_path")).startsWith(moved.toString());
            assertThat(rescan("MOVIE").get("added").asInt()).isZero();
            assertThat(rescan("TV_SHOW").get("removed").asInt()).isZero();
        } finally {
            Files.move(moved.resolve("filme"), library.resolve("filme"));
            Files.move(moved.resolve("serien"), library.resolve("serien"));
            Files.delete(moved);
        }
    }

    @Test
    void countsPathsWithoutARuleAndWarnsAboutUnreachableSources() throws IOException {
        byte[] backup = export(true);

        JsonNode report = runImport(backup, "REPLACE", List.of(Map.of("from", library.resolve("filme").toString(),
                "to", library.resolve("gibt-es-nicht").toString())), false);

        assertThat(report.get("unmappedPaths").asInt()).isPositive();
        assertThat(report.get("warnings").toString()).contains("gibt-es-nicht", "backup.warning.sourceUnreachable");
    }

    @Test
    void refusesAMappingToARelativePathBeforeChangingAnything() throws IOException {
        upload(export(true));

        MvcTestResult result = start("REPLACE", List.of(Map.of("from", library.toString(), "to", "kein/absoluter/pfad")),
                false);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("is not an absolute path", "backup.pathNotAbsolute");
        assertThat(count("movie")).isEqualTo(1);
    }

    @Test
    void dryRunChangesNothingAndKeepsTheUpload() throws IOException {
        byte[] backup = export(true);
        jdbcClient.sql("UPDATE movie SET title = 'Geändert'").update();

        JsonNode report = runImport(backup, "REPLACE", List.of(), true);

        assertThat(report.get("dryRun").asBoolean()).isTrue();
        assertThat(report.get("safetyCopy").isNull()).isTrue();
        assertThat(jdbcClient.sql("SELECT title FROM movie").query(String.class).single()).isEqualTo("Geändert");
        assertThat(start("REPLACE", List.of(), false)).hasStatus(HttpStatus.ACCEPTED);
        awaitJobsFinished();
    }

    @Test
    void aRealImportDeletesTheUploadAndWritesASafetyCopyWithTheOldState() throws IOException {
        byte[] backup = export(false);
        jdbcClient.sql("UPDATE movie SET title = 'Vorher'").update();

        JsonNode report = runImport(backup, "REPLACE", List.of(), false);

        Path copy = Path.of(report.get("safetyCopy").asString());
        String content = new String(gunzip(Files.readAllBytes(copy)), StandardCharsets.UTF_8);
        assertThat(content).contains("Vorher", API_KEY, KODI_PASSWORD);
        assertThat(start("REPLACE", List.of(), false)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void keepsOnlyTheLastThreeSafetyCopies() throws IOException {
        for (int run = 0; run < 5; run++) {
            runImport(export(true), "MERGE", List.of(), false);
        }

        try (var files = Files.list(home.resolve(BackupFiles.DIRECTORY_NAME))) {
            assertThat(files.filter(file -> file.getFileName().toString().startsWith("vor-import-"))).hasSize(3);
        }
    }

    @Test
    void keepsExistingCredentialsWhenTheFileHasNone() throws IOException {
        byte[] backup = export(false);

        JsonNode report = runImport(backup, "REPLACE", List.of(), false);

        assertThat(report.get("credentialsKept").asBoolean()).isTrue();
        assertThat(setting("plugin.tmdb.api-key")).isEqualTo(API_KEY);
        assertThat(kodiPassword()).isEqualTo(KODI_PASSWORD);
        assertThat(apiKeyService.view().getFirst().set()).isTrue();
    }

    @Test
    void keepsExistingCredentialsWhenMergingAFileWithoutThem() throws IOException {
        byte[] backup = export(false);

        runImport(backup, "MERGE", List.of(), false);

        assertThat(setting("plugin.tmdb.api-key")).isEqualTo(API_KEY);
        assertThat(kodiPassword()).isEqualTo(KODI_PASSWORD);
    }

    @Test
    void restoresCredentialsFromAFileThatHasThem() throws IOException {
        byte[] backup = export(true);
        apiKeyService.update(Map.of(ApiKey.TMDB_API_KEY, "anderer-key-12345"));
        jdbcClient.sql("UPDATE kodi_host SET password = 'anderes-passwort'").update();

        runImport(backup, "REPLACE", List.of(), false);

        assertThat(setting("plugin.tmdb.api-key")).isEqualTo(API_KEY);
        assertThat(kodiPassword()).isEqualTo(KODI_PASSWORD);
        assertThat(apiKeyService.view().getFirst().hint()).endsWith(API_KEY.substring(API_KEY.length() - 4));
    }

    @Test
    void reloadsSettingsAfterTheImport() throws IOException {
        byte[] backup = changedBackup(root -> {
            ObjectNode setting = ((ObjectNode) root.get(BackupFormat.TABLES)).putArray(BackupTables.APP_SETTING)
                    .addObject();
            setting.put(BackupTables.SETTING_KEY, "metadata-language");
            setting.put("setting_value", "fr-FR");
        });

        runImport(backup, "MERGE", List.of(), false);

        assertThat(settingsService.current().metadataLanguage()).isEqualTo("fr-FR");
    }

    @Test
    void mergeLetsTheFileWinAndKeepsEntriesThatOnlyExistHere() throws IOException {
        byte[] backup = export(true);
        jdbcClient.sql("UPDATE movie SET title = 'Geändert'").update();
        jdbcClient.sql("DELETE FROM movie_genre").update();
        jdbcClient.sql("INSERT INTO movie_genre (movie_id, genre_order, genre) SELECT id, 0, 'Falsch' FROM movie")
                .update();
        jdbcClient.sql("INSERT INTO saved_filter (media_type, name, definition) VALUES ('MOVIE', 'Bleibt', '{}')")
                .update();
        jdbcClient.sql("""
                INSERT INTO movie_set (name, sort_method) VALUES ('Nur hier', 'YEAR')""").update();

        JsonNode report = runImport(backup, "MERGE", List.of(), false);

        assertThat(jdbcClient.sql("SELECT title FROM movie").query(String.class).single()).isEqualTo("Heat");
        assertThat(jdbcClient.sql("SELECT genre FROM movie_genre").query(String.class).list()).containsExactly("Krimi");
        assertThat(count("saved_filter")).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM movie_set WHERE name = 'Nur hier'").query(Integer.class)
                .single()).isEqualTo(1);
        assertThat(result(report, "movie").get("updated").asInt()).isEqualTo(1);
        assertThat(result(report, "movie").get("created").asInt()).isZero();
    }

    @Test
    void mergeAddsNewEntriesAndKeepsTheirLinks() throws IOException {
        byte[] backup = export(true);
        jdbcClient.sql("DELETE FROM movie").update();
        jdbcClient.sql("DELETE FROM movie_set").update();

        JsonNode report = runImport(backup, "MERGE", List.of(), false);

        assertThat(result(report, "movie").get("created").asInt()).isEqualTo(1);
        assertThat(jdbcClient.sql("""
                SELECT s.name FROM movie m JOIN movie_set s ON m.movie_set_id = s.id""").query(String.class).single())
                .isEqualTo("Saga");
        assertThat(count("movie_credit")).isEqualTo(1);
    }

    @Test
    void mergeSkipsASourceThatOverlapsWithAnotherOneAndItsContent() throws IOException {
        byte[] backup = changedBackup(root -> {
            ObjectNode tables = (ObjectNode) root.get(BackupFormat.TABLES);
            ((ObjectNode) tables.get(BackupTables.MEDIA_SOURCE).get(0)).put("path",
                    library.resolve("filme/Heat").toString());
            ((ObjectNode) tables.get(BackupTables.MEDIA_SOURCE).get(0)).put("name", "Teilmenge");
            ((ObjectNode) tables.get(BackupTables.MOVIE).get(0)).put("file_path_hash", "x".repeat(64));
        });

        JsonNode report = runImport(backup, "MERGE", List.of(), false);

        assertThat(result(report, "media_source").get("skipped").asInt()).isEqualTo(1);
        assertThat(result(report, "movie").get("skipped").asInt()).isEqualTo(1);
        assertThat(report.get("warnings").toString()).contains("Teilmenge", "backup.warning.sourceOverlaps");
        assertThat(count("movie")).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT title FROM movie").query(String.class).single()).isEqualTo("Heat");
    }

    @Test
    void treatsNamesThatDifferOnlyInCaseOrAccentsAsTheSameEntry() throws IOException {
        byte[] backup = changedBackup(root -> {
            var sets = root.get(BackupFormat.TABLES).get(BackupTables.MOVIE_SET);
            ObjectNode other = ((ObjectNode) sets.get(0)).deepCopy();
            other.put("id", 99_999);
            other.put("name", "SÄGA");
            ((ArrayNode) sets).add(other);
            ((ObjectNode) sets.get(0)).put("name", "saga");
        });

        JsonNode report = runImport(backup, "REPLACE", List.of(), false);

        assertThat(count("movie_set")).isEqualTo(1);
        assertThat(result(report, "movie_set").get("created").asInt()).isEqualTo(1);
        assertThat(result(report, "movie_set").get("updated").asInt()).isEqualTo(1);
    }

    @Test
    void ignoresTablesAndColumnsItDoesNotKnow() throws IOException {
        byte[] backup = changedBackup(root -> {
            ObjectNode tables = (ObjectNode) root.get(BackupFormat.TABLES);
            tables.putArray("movie; DROP TABLE movie; --").addObject().put("id", 1);
            ((ObjectNode) tables.get(BackupTables.MOVIE).get(0)).put("title = 'x', plot", "böse");
        });

        runImport(backup, "REPLACE", List.of(), false);

        assertThat(jdbcClient.sql("SELECT title FROM movie").query(String.class).single()).isEqualTo("Heat");
    }

    @Test
    void acceptsAnOlderFileWithoutNewerColumns() throws IOException {
        byte[] backup = changedBackup(root -> {
            root.put(BackupFormat.SCHEMA_VERSION, "19");
            ((ObjectNode) root.get(BackupFormat.TABLES).get(BackupTables.MOVIE).get(0)).remove("nfo_read");
        });

        runImport(backup, "REPLACE", List.of(), false);

        assertThat(jdbcClient.sql("SELECT nfo_read FROM movie").query(Boolean.class).single()).isFalse();
    }

    @Test
    void refusesAFileFromANewerVersion() throws IOException {
        byte[] backup = changedBackup(root -> root.put(BackupFormat.SCHEMA_VERSION, "9999"));

        MvcTestResult result = upload(backup);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("newer KMM version", "backup.newerVersion");
    }

    @Test
    void refusesADamagedFileAndKeepsNoUpload() throws IOException {
        byte[] whole = export(true);
        byte[] truncated = Arrays.copyOf(whole, 40);

        assertThat(upload("kein gzip".getBytes(StandardCharsets.UTF_8))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(upload(truncated)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(upload(gzip("{\"tables\":{}}".getBytes(StandardCharsets.UTF_8)))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(start("REPLACE", List.of(), false)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rollsBackEverythingWhenARowIsInvalid() throws IOException {
        byte[] backup = changedBackup(root -> ((ObjectNode) root.get(BackupFormat.TABLES).get(BackupTables.EPISODE)
                .get(0)).put("added_at", "kein Datum"));
        jdbcClient.sql("UPDATE movie SET title = 'Vorher'").update();

        upload(backup);
        JsonNode job = awaitJob(start("REPLACE", List.of(), false));

        assertThat(job.get("status").asString()).isEqualTo("FAILED");
        assertThat(job.get("message").get("key").asString()).isEqualTo("backup.invalidValue");
        assertThat(jdbcClient.sql("SELECT title FROM movie").query(String.class).single()).isEqualTo("Vorher");
    }

    @Test
    void showsWhatTheFileContains() throws IOException {
        MvcTestResult result = upload(export(false));

        assertThat(result).hasStatusOk();
        JsonNode header = read(result);
        assertThat(header.get("credentials").asBoolean()).isFalse();
        assertThat(header.get("counts").get("movie").asInt()).isEqualTo(1);
        assertThat(header.get("sources")).hasSize(2);
        assertThat(header.get("sources").get(0).get("path").asString()).startsWith(library.toString());
    }

    private interface BackupChange {
        void apply(ObjectNode root);
    }

    private byte[] changedBackup(BackupChange change) throws IOException {
        ObjectNode root = (ObjectNode) objectMapper.readTree(gunzip(export(true)));
        change.apply(root);
        return gzip(objectMapper.writeValueAsBytes(root));
    }

    private byte[] export(boolean credentials) {
        MvcTestResult result = mockMvc.post().uri(BackupController.EXPORT_PATH)
                .param("credentials", String.valueOf(credentials)).with(csrf()).exchange();
        assertThat(result).hasStatusOk();
        return result.getResponse().getContentAsByteArray();
    }

    /** The file without IDs, foreign keys and header, sorted so that two states can be compared by content. */
    private JsonNode comparable(byte[] backup) throws IOException {
        ObjectNode tables = (ObjectNode) objectMapper.readTree(gunzip(backup)).get(BackupFormat.TABLES);
        ObjectNode result = objectMapper.createObjectNode();
        for (BackupTable table : BackupTables.ALL) {
            List<String> rows = tables.get(table.name()).valueStream().map(row -> {
                ObjectNode copy = ((ObjectNode) row).deepCopy();
                copy.remove("id");
                copy.remove(table.parents().keySet());
                return copy.toString();
            }).sorted().toList();
            var array = result.putArray(table.name());
            rows.forEach(array::add);
        }
        return result;
    }

    private MvcTestResult upload(byte[] content) {
        return mockMvc.post().uri(BackupController.UPLOAD_PATH)
                .multipart().file(new MockMultipartFile("file", "sicherung.json.gz", "application/gzip", content))
                .with(csrf()).exchange();
    }

    private MvcTestResult start(String mode, List<Map<String, String>> mappings, boolean dryRun) {
        return mockMvc.post().uri(BackupController.IMPORT_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("mode", mode, "pathMappings", mappings,
                        "dryRun", dryRun)))
                .exchange();
    }

    private JsonNode runImport(byte[] backup, String mode, List<Map<String, String>> mappings, boolean dryRun) {
        assertThat(upload(backup)).hasStatusOk();
        JsonNode job = awaitJob(start(mode, mappings, dryRun));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private JsonNode awaitJob(MvcTestResult started) {
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        return await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
    }

    private void awaitJobsFinished() {
        await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH).exchange()).valueStream()
                        .noneMatch(job -> "RUNNING".equals(job.get("status").asString())));
    }

    private void scan(String name, Path path, String mediaType) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "path", path.toString(),
                        "mediaType", mediaType)))
                .exchange();
        assertThat(source).hasStatus(HttpStatus.CREATED);
        runScan(read(source).get("id").asLong());
    }

    private JsonNode rescan(String mediaType) {
        long id = jdbcClient.sql("SELECT id FROM media_source WHERE media_type = ?").param(mediaType)
                .query(Long.class).single();
        return runScan(id).get("result");
    }

    private JsonNode runScan(long sourceId) {
        awaitJobsFinished();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread().until(() -> {
            String jobId = read(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange())
                    .get("id").asString();
            return read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange());
        }, node -> node != null);
        String jobId = job.get("id").asString();
        JsonNode finished = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        awaitJobsFinished();
        return finished;
    }

    private static JsonNode result(JsonNode report, String table) {
        return report.get("results").valueStream().filter(entry -> table.equals(entry.get("table").asString()))
                .findFirst().orElseThrow();
    }

    private String path(String table, String column) {
        return jdbcClient.sql("SELECT %s FROM %s".formatted(column, table)).query(String.class).list().getFirst();
    }

    private int count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    private String setting(String key) {
        return jdbcClient.sql("SELECT setting_value FROM app_setting WHERE setting_key = ?").param(key)
                .query(String.class).single();
    }

    private String kodiPassword() {
        return jdbcClient.sql("SELECT password FROM kodi_host").query(String.class).single();
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static byte[] gunzip(byte[] content) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return input.readAllBytes();
        }
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(target)) {
            output.write(content);
        }
        return target.toByteArray();
    }
}
