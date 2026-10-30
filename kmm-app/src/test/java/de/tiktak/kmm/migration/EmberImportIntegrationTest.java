package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.migration.ember.EmberFixtures;
import de.tiktak.kmm.kodi.KodiHostController;
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsController;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceController;
import de.tiktak.kmm.tvshow.TvShowController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ember-Import über die REST-API mit der Fixture-Datenbank; die Ember-Pfade ({@code D:\Filme} und
 * {@code \\nas\serien}) werden auf temporäre Verzeichnisse abgebildet.
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
    private static final String UPLOAD_PATH = EmberMigrationController.BASE_PATH + "/upload";

    @TempDir
    Path profile;

    @TempDir
    Path library;

    @TempDir
    Path series;

    @Value("${kmm.home}")
    private Path home;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private MovieMetadataService movieService;

    @BeforeEach
    void createProfileAndLibrary() throws IOException {
        EmberFixtures.createProfile(profile);
        createFile("Heat (1995)/Heat.mkv");
        Files.writeString(library.resolve("Heat (1995)/Heat.nfo"),
                "<movie><title>Heat aus der NFO</title><actor><name>Jemand</name></actor></movie>");
        createFile("The Matrix (1999)/The Matrix.mkv");
        createFile("The Matrix Reloaded (2003)/The Matrix Reloaded CD1.avi");
        createFile("The Matrix Reloaded (2003)/The Matrix Reloaded CD2.avi");
        createFile(series.resolve("Lost/Season 1/Lost S01E01E02.mkv"));
        createFile(series.resolve("Lost/Season 1/Lost S01E03.mkv"));
        Files.writeString(series.resolve("Lost/Season 1/Lost S01E03.nfo"),
                "<episodedetails><title>Aus der NFO</title><season>1</season><episode>3</episode></episodedetails>");
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
        assertThat(report.get("sourcesCreated").asInt()).isEqualTo(2);
        assertThat(report.get("moviesCreated").asInt()).isEqualTo(3);
        assertThat(report.get("tvShows")).hasToString(
                "{\"showsCreated\":1,\"showsUpdated\":0,\"showsSkipped\":0,\"episodesCreated\":3,"
                        + "\"episodesUpdated\":0,\"episodesSkipped\":0,\"episodesMissing\":1}");
        assertThat(report.get("moviesWithoutNfo").asInt()).isEqualTo(2);
        assertThat(report.get("settingsChanges").toString()).contains("Sprache der Metadaten: de-DE");
        assertThat(count("media_source")).isZero();
        assertThat(count("movie")).isZero();
        assertThat(count("movie_set")).isZero();
        assertThat(count("tv_show")).isZero();
        assertThat(count("episode")).isZero();
        assertThat(report.get("kodiHostsCreated").asInt()).isEqualTo(2);
        assertThat(count("kodi_host")).isZero();
        assertThat(mockMvc.get().uri(SettingsController.BASE_PATH)).bodyJson()
                .extractingPath("$.videoExtensions").asArray().contains("iso");
    }

    @Test
    void importsMoviesWithAllDetailsAndUpdatesThemOnRepeat() throws JSONException {
        JsonNode report = awaitJob(startImport(false));

        assertThat(report.get("moviesCreated").asInt()).isEqualTo(3);
        assertThat(report.get("skippedSources").isEmpty()).isTrue();
        long heatId = movieId("Heat");
        JSONAssert.assertEquals("""
                {"filePath": "%s", "addedAt": "%s",
                 "metadata": {"title": "Heat", "year": 1995, "mpaa": "FSK 16", "runtime": 170,
                              "genres": ["Krimi", "Drama"], "imdbId": "tt0113277", "tmdbId": "949"},
                 "supplement": {"actors": [{"name": "Al Pacino", "role": "Lt. Vincent Hanna"},
                                           {"name": "Robert De Niro", "role": "Neil McCauley"}],
                                "directors": ["Michael Mann"], "studios": ["Regency", "Warner Bros."],
                                "tags": ["Heist"], "userRating": 9, "playCount": 2,
                                "ratings": [{"source": "imdb", "value": 8.3}, {"source": "themoviedb"}]},
                 "nfo": {"exists": true}}
                """.formatted(json(library.resolve("Heat (1995)/Heat.mkv").toString()), EMBER_DATE_ADDED),
                objectMapper.writeValueAsString(movieService.findDetails(heatId)), false);
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, movieId("Matrix Reloaded"))).bodyJson()
                .extractingPath("$.supplement.set.name").isEqualTo("Matrix Collection");
        assertThat(jdbcClient.sql("SELECT tmdb_collection_id FROM movie_set WHERE name = 'Matrix Collection'")
                .query(String.class).single()).isEqualTo("2344");
        assertThat(jdbcClient.sql("SELECT sort_method FROM movie_set WHERE name = 'Leeres Set'").query(String.class)
                .single()).as("auch Sets ohne Filme").isEqualTo("TITLE");
        assertThat(jdbcClient.sql("SELECT part_count FROM movie WHERE title = 'Matrix Reloaded'").query(Integer.class)
                .single()).isEqualTo(2);

        JsonNode repeated = awaitJob(startImport(false));

        assertThat(repeated.get("moviesCreated").asInt()).isZero();
        assertThat(repeated.get("moviesUpdated").asInt()).isEqualTo(3);
        assertThat(repeated.get("sourcesReused").asInt()).isEqualTo(2);
        assertThat(repeated.get("kodiHostsUpdated").asInt()).isEqualTo(2);
        assertThat(count("movie")).isEqualTo(3);
    }

    @Test
    void keepsImportedDataWhenTheSourceIsScanned() {
        awaitJob(startImport(false));
        long sourceId = sourceId("MOVIE");

        JsonNode scan = awaitJob(post(
                LibraryScanController.SCAN_PATH.replace("{sourceId}", String.valueOf(sourceId))));

        assertThat(scan.get("added").asInt()).isZero();
        assertThat(scan.get("removed").asInt()).isZero();
        assertThat(movieService.findDetails(movieId("Heat")).metadata().title())
                .as("der Scan behält Embers Werte").isEqualTo("Heat");
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, movieId("Heat"))).bodyJson()
                .extractingPath("$.metadata.title").as("beim Öffnen gilt die NFO").isEqualTo("Heat aus der NFO");
    }

    @Test
    void importsTvShowsWithEpisodesAndUpdatesThemOnRepeat() {
        JsonNode report = awaitJob(startImport(false));

        assertThat(report.get("tvShows").get("episodesCreated").asInt()).isEqualTo(3);
        long showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();
        assertThat(mockMvc.get().uri(TvShowController.SHOW_PATH, showId)).bodyJson().isLenientlyEqualTo("""
                {"directoryPath": "%s",
                 "metadata": {"title": "Lost", "year": 2004, "mpaa": "FSK 16", "status": "Ended", "runtime": 45,
                              "genres": ["Drama"], "studios": ["Warner Bros."], "tags": ["Heist"],
                              "tvdbId": "73739", "tmdbId": "4607", "imdbId": "tt0411008", "userRating": 8},
                 "extras": {"actors": [{"name": "Matthew Fox", "role": "Jack Shephard"}],
                            "ratings": [{"source": "imdb", "value": 9.9}], "seasonNames": {"0": "Specials"}},
                 "seasons": [{"number": 0, "title": "Specials", "episodeCount": 0},
                             {"number": 1, "episodeCount": 3}]}
                """.formatted(json(series.resolve("Lost").toString())));
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, episodeId(1))).bodyJson().isLenientlyEqualTo("""
                {"filePath": "%s", "season": 1, "episode": 1, "addedAt": "%s",
                 "metadata": {"title": "Pilot (1)", "plot": "Der Absturz.", "aired": "2004-09-22", "runtime": 42,
                              "tvdbId": "127131", "tmdbId": "64465", "imdbId": "tt0636289", "userRating": 9},
                 "extras": {"directors": ["Jack Bender"], "writers": ["Jack Bender"],
                            "actors": [{"name": "Matthew Fox"}, {"name": "Kevin Tighe", "role": "Anthony Cooper"}],
                            "ratings": [{"source": "tvdb", "value": 8.1, "votes": 120}], "playCount": 1,
                            "streams": {"videos": [{"codec": "h264", "aspect": 1.778, "width": 1280, "height": 720}],
                                        "audios": [{"codec": "AAC", "language": "deu", "channels": 2}],
                                        "subtitles": [{"language": "eng", "forced": false}]}},
                 "nfo": {"exists": false}}
                """.formatted(json(series.resolve("Lost/Season 1/Lost S01E01E02.mkv").toString()), EMBER_DATE_ADDED));
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, episodeId(2))).bodyJson()
                .extractingPath("$.filePath").isEqualTo(series.resolve("Lost/Season 1/Lost S01E01E02.mkv").toString());

        JsonNode repeated = awaitJob(startImport(false));

        assertThat(repeated.get("tvShows").get("showsUpdated").asInt()).isEqualTo(1);
        assertThat(repeated.get("tvShows").get("episodesUpdated").asInt()).isEqualTo(3);
        assertThat(count("tv_show")).isEqualTo(1);
        assertThat(count("episode")).isEqualTo(3);
    }

    @Test
    void keepsImportedEpisodesWhenTheTvSourceIsScanned() {
        awaitJob(startImport(false));

        JsonNode scan = awaitJob(post(
                LibraryScanController.SCAN_PATH.replace("{sourceId}", String.valueOf(sourceId("TV_SHOW")))));

        assertThat(scan.get("added").asInt()).as(scan.toString()).isZero();
        assertThat(scan.get("removed").asInt()).isZero();
        assertThat(jdbcClient.sql("SELECT title FROM episode WHERE id = :id").param("id", episodeId(3))
                .query(String.class).single()).as("der Scan behält Embers Werte").isEqualTo("Tabula Rasa");
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, episodeId(3))).bodyJson()
                .extractingPath("$.metadata.title").as("beim Öffnen gilt die NFO").isEqualTo("Aus der NFO");
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
    void rejectsAnImportWithoutUpload() {
        assertThat(mockMvc.delete().uri(UPLOAD_PATH).with(csrf())).hasStatus(204);

        assertThat(mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "pathMappings", List.of(), "importSettings", false, "importKodiHosts", false,
                        "importTvShows", false, "dryRun", true))))
                .hasStatus(422)
                .bodyJson().extractingPath("$.detail").asString().contains("hochladen");
    }

    @Test
    void deletesTheUploadAfterTheImportButNotAfterADryRun() {
        awaitJob(startImport(true));
        assertThat(uploadDirectory()).isDirectory();

        awaitJob(startImport(false));
        assertThat(uploadDirectory()).doesNotExist();
    }

    @Test
    void listsEmberSourcesForPathMappings() {
        assertThat(upload())
                .bodyJson().isLenientlyEqualTo("""
                        {"sources": [{"name": "Filme", "path": "D:\\\\Filme"}],
                         "tvSources": [{"name": "Serien", "path": "\\\\\\\\nas\\\\serien"}],
                         "kodiHosts": [{"name": "Wohnzimmer", "address": "kodi.example.org", "pathMappings": 2},
                                       {"name": null, "address": "192.168.1.20", "port": 0}]}
                        """);
    }

    private MvcTestResult upload() {
        var request = mockMvc.post().uri(UPLOAD_PATH).with(csrf()).multipart();
        try (var files = Files.list(profile)) {
            for (Path file : files.filter(EmberImportIntegrationTest::belongsToUpload).toList()) {
                request = request.file(new MockMultipartFile("files", file.getFileName().toString(), null,
                        Files.readAllBytes(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return request.exchange();
    }

    private static boolean belongsToUpload(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".emm") || name.endsWith(".xml");
    }

    private Path uploadDirectory() {
        return home.resolve(EmberProfileUpload.DIRECTORY_NAME);
    }

    private String startImport(boolean dryRun) {
        assertThat(upload()).hasStatusOk();
        MvcTestResult result = mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "pathMappings", List.of(Map.of("emberPath", "D:\\Filme", "kmmPath", library.toString()),
                                Map.of("emberPath", "\\\\nas\\serien", "kmmPath", series.toString())),
                        "importSettings", true,
                        "importKodiHosts", true,
                        "importTvShows", true,
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

    private long sourceId(String mediaType) {
        return jdbcClient.sql("SELECT id FROM media_source WHERE media_type = :type").param("type", mediaType)
                .query(Long.class).single();
    }

    private long episodeId(int episode) {
        return jdbcClient.sql("SELECT id FROM episode WHERE episode_number = :episode").param("episode", episode)
                .query(Long.class).single();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private void createFile(String relativePath) throws IOException {
        createFile(library.resolve(relativePath));
    }

    private static void createFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\");
    }
}
