package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.kodi.rpc.FakeKodiServer;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.source.MediaSourceController;
import de.tiktak.kmm.tvshow.TvShowController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Realtime sync end to end against a fake Kodi: after a scan with new movies Kodi scans the source, after saving
 * metadata Kodi reads the movie again.
 */
@SpringBootTest(properties = "kmm.kodi.sync-delay=50ms")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class KodiSyncIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String KODI_FILE = "smb://nas/filme/Heat (1995)/Heat (1995).mkv";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path library;

    private final FakeKodiServer kodi = new FakeKodiServer();

    @AfterEach
    void cleanUp() {
        kodi.close();
        jdbcClient.sql("DELETE FROM media_source").update();
        jdbcClient.sql("DELETE FROM kodi_host").update();
    }

    @Test
    void keepsKodiUpToDate() throws IOException {
        Files.createDirectories(library.resolve("Heat (1995)"));
        Files.createFile(library.resolve("Heat (1995)/Heat (1995).mkv"));
        kodi.respond("Files.GetSources", "{\"sources\": [{\"file\": \"smb://nas/filme/\"}]}");
        kodi.respond("VideoLibrary.GetMovies", "{\"movies\": [{\"movieid\": 7, \"file\": \"%s\"}]}".formatted(KODI_FILE));
        createKodiHost();
        long sourceId = create(MediaSourceController.BASE_PATH,
                Map.of("name", "Filme", "path", library.toString(), "mediaType", "MOVIE"));

        scan(sourceId);
        await().atMost(TIMEOUT).until(() -> kodi.methods().contains("VideoLibrary.Scan"));
        assertThat(kodi.calls().get(2).params().path("directory").asString()).isEqualTo("smb://nas/filme/");

        long movieId = read(mockMvc.get().uri(MovieController.MOVIES_PATH).exchange())
                .get("content").get(0).get("id").asLong();
        assertThat(mockMvc.put().uri(MovieController.METADATA_PATH, movieId).with(csrf()).contentType(JSON)
                .content("{\"title\": \"Heat\", \"year\": 1995, \"genres\": []}"))
                .hasStatusOk();
        await().atMost(TIMEOUT).until(() -> kodi.methods().contains("VideoLibrary.RefreshMovie"));
        assertThat(kodi.methods()).containsExactly("Files.GetSources", "XBMC.GetInfoBooleans", "VideoLibrary.Scan",
                "VideoLibrary.GetMovies", "VideoLibrary.RefreshMovie");

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/{id}/refresh", movieId).with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"host": "Wohnzimmer", "outcome": "REFRESHED",
                          "message": {"key": "kodi.sync.refreshed", "args": {"title": "Heat"}}}]
                        """);
        assertThat(mockMvc.get().uri(KodiHostController.BASE_PATH))
                .bodyJson().extractingPath("$[0].lastSync.outcome").isEqualTo("REFRESHED");
    }

    @Test
    void keepsKodiUpToDateForTvShows() throws IOException {
        Path episode = library.resolve("Lost/Staffel 1/Lost - S01E01.mkv");
        Files.createDirectories(episode.getParent());
        Files.createFile(episode);
        kodi.respond("VideoLibrary.GetTVShows", "{\"tvshows\": [{\"tvshowid\": 4, \"file\": \"smb://nas/filme/Lost/\"}]}");
        kodi.respond("VideoLibrary.GetEpisodes", """
                {"episodes": [{"episodeid": 9, "file": "smb://nas/filme/Lost/Staffel 1/Lost - S01E01.mkv",
                               "season": 1, "episode": 1}]}""");
        createKodiHost();
        long sourceId = create(MediaSourceController.BASE_PATH,
                Map.of("name", "Serien", "path", library.toString(), "mediaType", "TV_SHOW"));

        scan(sourceId);
        await().atMost(TIMEOUT).until(() -> kodi.methods().contains("VideoLibrary.Scan"));

        long showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();
        long episodeId = jdbcClient.sql("SELECT id FROM episode").query(Long.class).single();
        assertThat(mockMvc.put().uri(TvShowController.SHOW_METADATA_PATH, showId).with(csrf()).contentType(JSON)
                .content("{\"title\": \"Lost\", \"genres\": [], \"studios\": [], \"tags\": []}"))
                .hasStatusOk();
        await().atMost(TIMEOUT).until(() -> kodi.methods().contains("VideoLibrary.RefreshTVShow"));
        assertThat(mockMvc.put().uri(TvShowController.EPISODE_METADATA_PATH, episodeId).with(csrf())
                .contentType(JSON).content("{\"title\": \"Gestrandet\"}"))
                .hasStatusOk();
        await().atMost(TIMEOUT).until(() -> kodi.methods().contains("VideoLibrary.RefreshEpisode"));
        assertThat(kodi.methods()).containsExactly("Files.GetSources", "XBMC.GetInfoBooleans", "VideoLibrary.Scan",
                "VideoLibrary.GetTVShows", "VideoLibrary.RefreshTVShow", "VideoLibrary.GetEpisodes",
                "VideoLibrary.RefreshEpisode");

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/episodes/{id}/refresh", episodeId)
                .with(csrf())).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"host": "Wohnzimmer", "outcome": "REFRESHED",
                          "message": {"key": "kodi.sync.refreshed", "args": {"title": "Lost S01E01"}}}]
                        """);
    }

    private void createKodiHost() {
        create(KodiHostController.BASE_PATH, Map.of("name", "Wohnzimmer", "address", "127.0.0.1",
                "port", kodi.port(), "realtimeSync", true,
                "pathMappings", List.of(Map.of("localPath", library.toString(), "kodiPath", "smb://nas/filme/"))));
    }

    private long create(String path, Map<String, Object> body) {
        MvcTestResult result = mockMvc.post().uri(path).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(body)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private void scan(long sourceId) {
        MvcTestResult started = mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf())
                .exchange();
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
