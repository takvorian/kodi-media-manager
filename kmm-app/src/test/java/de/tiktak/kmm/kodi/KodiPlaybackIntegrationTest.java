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
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Watched status from Kodi end to end against a fake Kodi: the state ends up in NFO and database, Kodi is not asked to
 * scan again afterwards, and the next scan considers the NFOs up to date.
 */
@SpringBootTest(properties = "kmm.kodi.sync-delay=50ms")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class KodiPlaybackIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

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
    void takesOverThePlaybackOfAMovieSource() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path heatNfo = givenFile(filme.resolve("Heat (1995)/Heat (1995).nfo"),
                "<movie><title>Heat</title><playcount>0</playcount></movie>");
        givenFile(filme.resolve("Heat (1995)/Heat (1995).mkv"), "");
        givenFile(filme.resolve("Ronin (1998)/Ronin (1998).mkv"), "");
        givenFile(filme.resolve("Alien (1979)/Alien (1979).mkv"), "");
        kodi.respond("VideoLibrary.GetMovies", """
                {"limits": {"total": 2}, "movies": [
                  {"file": "smb://nas/medien/filme/Heat (1995)/Heat (1995).mkv", "playcount": 2,
                   "lastplayed": "2025-04-03 23:14:16", "userrating": 9},
                  {"file": "smb://nas/medien/filme/Ronin (1998)/Ronin (1998).mkv", "playcount": 1, "lastplayed": ""}]}
                """);
        createKodiHost();
        long sourceId = createSource("Filme", filme, "MOVIE");
        scan(sourceId);

        JsonNode result = runJob(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/sources/{id}/playback", sourceId)
                .with(csrf()).exchange());

        assertThat(result.toString()).contains("\"updated\":2", "\"unchanged\":0", "\"notInKodi\":1",
                "\"failed\":0", "\"host\":\"Wohnzimmer\"");
        assertThat(Files.readString(heatNfo)).contains("<title>Heat</title>", "<playcount>2</playcount>",
                "<lastplayed>2025-04-03 23:14:16</lastplayed>", "<userrating>9</userrating>");
        assertThat(jdbcClient.sql("SELECT user_rating FROM movie WHERE title = 'Heat'").query(Integer.class).single())
                .isEqualTo(9);
        assertThat(jdbcClient.sql("SELECT play_count FROM movie ORDER BY title").query(Integer.class).list())
                .containsExactly(null, 2, 1);
        long alienId = jdbcClient.sql("SELECT id FROM movie WHERE title LIKE 'Alien%'").query(Long.class).single();
        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/{id}/playback", alienId).with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"host": "Wohnzimmer", "outcome": "NOT_IN_KODI",
                         "message": {"key": "kodi.playback.notInKodi", "args": {"label": "Alien (1979)"}}}
                        """);

        JsonNode again = runJob(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/sources/{id}/playback",
                sourceId).with(csrf()).exchange());
        assertThat(again.toString()).contains("\"updated\":0", "\"unchanged\":2");
        assertThat(scan(sourceId).toString()).contains("\"updated\":0", "\"unchanged\":3");
        assertThat(kodi.methods()).doesNotContain("VideoLibrary.RefreshMovie");
    }

    @Test
    void takesOverThePlaybackOfASingleEpisodeOfAMultiEpisodeFile() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        Path nfo = givenFile(serien.resolve("Lost/Lost - S01E01E02.nfo"), """
                <episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>
                <episodedetails><title>Zwei</title><season>1</season><episode>2</episode></episodedetails>""");
        givenFile(serien.resolve("Lost/Lost - S01E01E02.mkv"), "");
        kodi.respond("VideoLibrary.GetEpisodes", """
                {"episodes": [
                  {"file": "smb://nas/medien/serien/Lost/Lost - S01E01E02.mkv", "season": 1, "episode": 1,
                   "playcount": 1, "lastplayed": "2024-01-01 20:00:00"},
                  {"file": "smb://nas/medien/serien/Lost/Lost - S01E01E02.mkv", "season": 1, "episode": 2,
                   "playcount": 0, "lastplayed": ""}]}
                """);
        createKodiHost();
        long sourceId = createSource("Serien", serien, "TV_SHOW");
        scan(sourceId);
        long first = jdbcClient.sql("SELECT id FROM episode WHERE episode_number = 1").query(Long.class).single();

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/episodes/{id}/playback", first).with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"outcome": "UPDATED", "message": {"key": "kodi.playback.updated",
                         "args": {"label": "Lost S01E01", "playCount": 1, "rating": "none"}}, "playCount": 1}
                        """);

        List<String> lines = Files.readAllLines(nfo);
        assertThat(String.join("", lines)).contains("<title>Eins</title>", "<playcount>1</playcount>",
                "<lastplayed>2024-01-01 20:00:00</lastplayed>", "<title>Zwei</title>");
        assertThat(lines.stream().filter(line -> line.contains("playcount"))).hasSize(1);
        assertThat(scan(sourceId).toString()).contains("\"updated\":0", "\"unchanged\":2");
    }

    private Path givenFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private void createKodiHost() {
        create(KodiHostController.BASE_PATH, Map.of("name", "Wohnzimmer", "address", "127.0.0.1",
                "port", kodi.port(), "realtimeSync", true,
                "pathMappings", List.of(Map.of("localPath", library.toString(), "kodiPath", "smb://nas/medien/"))));
    }

    private long createSource(String name, Path path, String mediaType) {
        return create(MediaSourceController.BASE_PATH,
                Map.of("name", name, "path", path.toString(), "mediaType", mediaType));
    }

    private long create(String path, Map<String, Object> body) {
        MvcTestResult result = mockMvc.post().uri(path).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(body)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private JsonNode scan(long sourceId) {
        return runJob(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange());
    }

    /** @return the result of the successfully finished job */
    private JsonNode runJob(MvcTestResult started) {
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
