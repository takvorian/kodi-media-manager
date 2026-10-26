package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.migration.ember.EmberFixtures;
import de.tiktak.kmm.kodi.KodiHostController;
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsController;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ember-Import über die REST-API mit der Fixture-Datenbank; die Ember-Pfade ({@code D:\Filme}) werden
 * auf ein temporäres Verzeichnis abgebildet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class EmberImportIntegrationTest {

    /** Ember speichert lokale Uhrzeiten (hier 30.11.2016 00:20:15); KMM rechnet mit der eigenen Zeitzone um. */
    private static final Instant EMBER_DATE_ADDED =
            LocalDateTime.of(2016, 11, 30, 0, 20, 15).atZone(ZoneId.systemDefault()).toInstant();
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(20);
    private static final String IMPORT_PATH = EmberMigrationController.BASE_PATH + "/import";

    @TempDir
    Path profile;

    @TempDir
    Path library;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SettingsService settingsService;

    @BeforeEach
    void createProfileAndLibrary() throws IOException {
        EmberFixtures.createProfile(profile);
        createFile("Heat (1995)/Heat.mkv");
        Files.writeString(library.resolve("Heat (1995)/Heat.nfo"),
                "<movie><title>Heat aus der NFO</title><actor><name>Jemand</name></actor></movie>");
        createFile("The Matrix (1999)/The Matrix.mkv");
        createFile("The Matrix Reloaded (2003)/The Matrix Reloaded CD1.avi");
        createFile("The Matrix Reloaded (2003)/The Matrix Reloaded CD2.avi");
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
        jdbcClient.sql("DELETE FROM movie_set").update();
        jdbcClient.sql("DELETE FROM kodi_host").update();
        settingsService.update(KmmSettings.DEFAULTS);
    }

    @Test
    void dryRunReportsTheImportWithoutSavingAnything() {
        JsonNode report = awaitJob(startImport(true));

        assertThat(report.get("dryRun").asBoolean()).isTrue();
        assertThat(report.get("sourcesCreated").asInt()).isEqualTo(1);
        assertThat(report.get("moviesCreated").asInt()).isEqualTo(3);
        assertThat(report.get("moviesWithoutNfo").asInt()).isEqualTo(2);
        assertThat(report.get("settingsChanges").toString()).contains("Sprache der Metadaten: de-DE");
        assertThat(count("media_source")).isZero();
        assertThat(count("movie")).isZero();
        assertThat(count("movie_set")).isZero();
        assertThat(report.get("kodiHostsCreated").asInt()).isEqualTo(2);
        assertThat(count("kodi_host")).isZero();
        assertThat(mockMvc.get().uri(SettingsController.BASE_PATH)).bodyJson()
                .extractingPath("$.videoExtensions").asArray().contains("iso");
    }

    @Test
    void importsMoviesWithAllDetailsAndUpdatesThemOnRepeat() {
        JsonNode report = awaitJob(startImport(false));

        assertThat(report.get("moviesCreated").asInt()).isEqualTo(3);
        assertThat(report.get("skippedSources").isEmpty()).isTrue();
        long heatId = movieId("Heat");
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, heatId)).bodyJson().isLenientlyEqualTo("""
                {"filePath": "%s", "addedAt": "%s",
                 "metadata": {"title": "Heat", "year": 1995, "mpaa": "FSK 16", "runtime": 170,
                              "genres": ["Krimi", "Drama"], "imdbId": "tt0113277", "tmdbId": "949"},
                 "supplement": {"actors": [{"name": "Al Pacino", "role": "Lt. Vincent Hanna"},
                                           {"name": "Robert De Niro", "role": "Neil McCauley"}],
                                "directors": ["Michael Mann"], "studios": ["Regency", "Warner Bros."],
                                "tags": ["Heist"], "userRating": 9, "playCount": 2,
                                "ratings": [{"source": "imdb", "value": 8.3}, {"source": "themoviedb"}]},
                 "nfo": {"exists": true}}
                """.formatted(json(library.resolve("Heat (1995)/Heat.mkv").toString()), EMBER_DATE_ADDED));
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, movieId("Matrix Reloaded"))).bodyJson()
                .extractingPath("$.supplement.set.name").isEqualTo("Matrix Collection");
        assertThat(jdbcClient.sql("SELECT tmdb_collection_id FROM movie_set").query(String.class).single())
                .isEqualTo("2344");
        assertThat(jdbcClient.sql("SELECT part_count FROM movie WHERE title = 'Matrix Reloaded'").query(Integer.class)
                .single()).isEqualTo(2);

        JsonNode repeated = awaitJob(startImport(false));

        assertThat(repeated.get("moviesCreated").asInt()).isZero();
        assertThat(repeated.get("moviesUpdated").asInt()).isEqualTo(3);
        assertThat(repeated.get("sourcesReused").asInt()).isEqualTo(1);
        assertThat(repeated.get("kodiHostsUpdated").asInt()).isEqualTo(2);
        assertThat(count("movie")).isEqualTo(3);
    }

    @Test
    void keepsImportedDataWhenTheSourceIsScanned() {
        awaitJob(startImport(false));
        long sourceId = jdbcClient.sql("SELECT id FROM media_source").query(Long.class).single();

        JsonNode scan = awaitJob(post(MovieController.SCAN_PATH.replace("{sourceId}", String.valueOf(sourceId))));

        assertThat(scan.get("added").asInt()).isZero();
        assertThat(scan.get("removed").asInt()).isZero();
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, movieId("Heat"))).bodyJson()
                .extractingPath("$.metadata.title").isEqualTo("Heat");
    }

    @Test
    void importsSettingsWithMappedExcludedPaths() {
        awaitJob(startImport(false));

        assertThat(mockMvc.get().uri(SettingsController.BASE_PATH)).bodyJson().isLenientlyEqualTo("""
                {"metadataLanguage": "de-DE", "artworkLanguage": "de", "videoExtensions": ["mkv", "mp4", "avi"],
                 "excludedPaths": ["%s"], "proxy": {"host": "proxy.local", "port": 3128, "username": "proxyuser",
                 "passwordSet": false}}
                """.formatted(json(library.resolve("Privat").toString())));
    }

    @Test
    void importsKodiHostsWithCredentialsAndMappedPaths() {
        JsonNode report = awaitJob(startImport(false));

        assertThat(report.get("warnings").toString()).contains("Wohnzimmer").contains("smb://nas/medien/serien/");
        assertThat(mockMvc.get().uri(KodiHostController.BASE_PATH)).bodyJson().isLenientlyEqualTo("""
                [{"name": "192.168.1.20", "address": "192.168.1.20", "port": 8080, "passwordSet": false,
                  "realtimeSync": false, "pathMappings": []},
                 {"name": "Wohnzimmer", "address": "kodi.example.org", "port": 8080, "username": "kodi",
                  "passwordSet": true, "realtimeSync": true,
                  "pathMappings": [{"localPath": "%s", "kodiPath": "smb://nas/medien/filme/"}]}]
                """.formatted(json(library.toString())));
    }

    @Test
    void rejectsInvalidProfile() {
        assertThat(mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("profilePath", library.toString(),
                        "pathMappings", List.of(), "importSettings", false, "importKodiHosts", false,
                        "dryRun", true))))
                .hasStatus(422);
    }

    @Test
    void listsEmberSourcesForPathMappings() {
        assertThat(mockMvc.get().uri(EmberMigrationController.BASE_PATH + "/inspect").param("path", profile.toString()))
                .bodyJson().isLenientlyEqualTo("""
                        {"sources": [{"name": "Filme", "path": "D:\\\\Filme"}],
                         "kodiHosts": [{"name": "Wohnzimmer", "address": "kodi.example.org", "pathMappings": 2},
                                       {"name": null, "address": "192.168.1.20", "port": 0}]}
                        """);
    }

    private String startImport(boolean dryRun) {
        MvcTestResult result = mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "profilePath", profile.toString(),
                        "pathMappings", List.of(Map.of("emberPath", "D:\\Filme", "kmmPath", library.toString())),
                        "importSettings", true,
                        "importKodiHosts", true,
                        "dryRun", dryRun)))
                .exchange();
        assertThat(result).hasStatus(202);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String post(String path) {
        MvcTestResult result = mockMvc.post().uri(path).with(csrf()).exchange();
        assertThat(result).hasStatus(202);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private JsonNode awaitJob(String jobId) {
        JsonNode job = await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> readJob(jobId), node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private JsonNode readJob(String jobId) {
        MvcTestResult result = mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private long movieId(String title) {
        return jdbcClient.sql("SELECT id FROM movie WHERE title = :title").param("title", title).query(Long.class)
                .single();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private void createFile(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\");
    }
}
