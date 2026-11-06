package de.tiktak.kmm.mediainfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.media.MediaProbe;
import de.tiktak.kmm.media.FfprobeRunner;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Technical details end to end with a fake ffprobe: they end up in the NFO and database,
 * languages from the NFO are kept, and the next scan considers the NFOs up to date.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class StreamDetailsIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final StreamDetails PROBED = new StreamDetails(
            List.of(new StreamDetails.Video("hevc", 2.4, 3840, 1600, 7289, 20000, "Progressive", null, null, "hdr10",
                    null, null, 1000L)),
            List.of(new StreamDetails.Audio("truehd", null, 8, null)),
            List.of());

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private MediaProbes probes;

    @TempDir
    Path library;

    private final MediaProbe probe = mock(MediaProbe.class);

    @BeforeEach
    void fakeFfprobe() {
        when(probes.current()).thenReturn(probe);
        when(probes.probeNewFiles()).thenReturn(true);
        when(probe.probe(any(), any())).thenReturn(PROBED);
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void readsNewFilesAfterTheScanAndReplacesAllDetailsOfASourceKeepingLanguages() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path heatNfo = givenFile(filme.resolve("Heat (1995)/Heat (1995).nfo"), String.join("\r\n",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<movie>",
                "  <title>Heat</title>",
                "  <fileinfo>",
                "    <streamdetails>",
                "      <audio>",
                "        <codec>dca</codec>",
                "        <language>deu</language>",
                "      </audio>",
                "    </streamdetails>",
                "  </fileinfo>",
                "</movie>",
                ""));
        givenFile(filme.resolve("Heat (1995)/Heat (1995).mkv"), "");
        givenFile(filme.resolve("Ronin (1998)/Ronin (1998).mkv"), "");
        long sourceId = createSource("Filme", filme, "MOVIE");

        scan(sourceId);

        await().atMost(TIMEOUT).until(() -> streamsOf("Ronin (1998)") != null);
        assertThat(streamsOf("Ronin (1998)")).contains("\"codec\":\"truehd\"");
        assertThat(streamsOf("Heat")).contains("\"codec\":\"dca\"");

        JsonNode result = runJob(mockMvc.post().uri("/api/v1/sources/{id}/stream-details?scope=ALL", sourceId)
                .with(csrf()).exchange());

        assertThat(result.toString()).contains("\"updated\":2", "\"skipped\":0", "\"failed\":0");
        String nfo = Files.readString(heatNfo);
        assertThat(nfo).startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<movie>\r\n  <title>Heat</title>\r\n")
                .contains("        <codec>truehd</codec>\r\n        <language>deu</language>\r\n"
                        + "        <longlanguage>German</longlanguage>\r\n", "<hdrtype>hdr10</hdrtype>")
                .doesNotContain("dca");
        assertThat(streamsOf("Heat")).contains("\"language\":\"deu\"", "\"hdrType\":\"hdr10\"");
        assertThat(scan(sourceId).toString()).contains("\"updated\":0", "\"unchanged\":2");

        JsonNode missingOnly = runJob(mockMvc.post().uri("/api/v1/sources/{id}/stream-details", sourceId)
                .with(csrf()).exchange());
        assertThat(missingOnly.toString()).contains("\"updated\":0", "\"skipped\":2");
    }

    @Test
    void writesTheDetailsOfAMultiEpisodeFileIntoEveryEpisode() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        Path nfo = givenFile(serien.resolve("Lost/Lost - S01E01E02.nfo"), """
                <episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>
                <episodedetails><title>Zwei</title><season>1</season><episode>2</episode></episodedetails>""");
        givenFile(serien.resolve("Lost/Lost - S01E01E02.mkv"), "");
        when(probes.probeNewFiles()).thenReturn(false);
        long sourceId = createSource("Serien", serien, "TV_SHOW");
        scan(sourceId);
        long first = jdbcClient.sql("SELECT id FROM episode WHERE episode_number = 1").query(Long.class).single();

        assertThat(mockMvc.post().uri("/api/v1/episodes/{id}/stream-details", first).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.audios[0].codec").isEqualTo("truehd");

        assertThat(Files.readString(nfo).split("<fileinfo>", -1)).hasSize(3);
        assertThat(jdbcClient.sql("SELECT stream_details FROM episode ORDER BY episode_number").query(String.class)
                .list()).allSatisfy(streams -> assertThat(streams).contains("truehd"));
        assertThat(scan(sourceId).toString()).contains("\"updated\":0", "\"unchanged\":2");
    }

    @Test
    void rereadsEveryEpisodeOfOneSeason() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        givenFile(serien.resolve("Lost/Staffel 1/Lost - S01E01E02.mkv"), "");
        givenFile(serien.resolve("Lost/Staffel 1/Lost - S01E03.mkv"), "");
        givenFile(serien.resolve("Lost/Staffel 2/Lost - S02E01.mkv"), "");
        when(probes.probeNewFiles()).thenReturn(false);
        scan(createSource("Serien", serien, "TV_SHOW"));
        long lost = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();

        JsonNode result = runJob(mockMvc.post().uri("/api/v1/tvshows/{id}/seasons/1/stream-details", lost)
                .with(csrf()).exchange());

        assertThat(result.toString()).contains("\"updated\":2", "\"skipped\":0", "\"failed\":0");
        assertThat(jdbcClient.sql("SELECT season_number, stream_details FROM episode ORDER BY season_number, "
                + "episode_number").query((row, index) -> row.getInt(1) + ":" + (row.getString(2) != null)).list())
                .containsExactly("1:true", "1:true", "1:true", "2:false");
        assertThat(mockMvc.post().uri("/api/v1/tvshows/{id}/seasons/5/stream-details", lost).with(csrf()))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail")
                .isEqualTo("“Lost” has no episodes in season 5.");
    }

    @Test
    void reportsFilesFfprobeCannotRead() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        givenFile(filme.resolve("Heat (1995)/Heat (1995).mkv"), "");
        when(probes.probeNewFiles()).thenReturn(false);
        MediaProbe missingFfprobe = new MediaProbe(new FfprobeRunner(library.resolve("fehlt").toString(),
                Duration.ofSeconds(5)));
        when(probes.current()).thenReturn(missingFfprobe);
        long sourceId = createSource("Filme", filme, "MOVIE");
        scan(sourceId);
        long heat = jdbcClient.sql("SELECT id FROM movie").query(Long.class).single();

        assertThat(mockMvc.post().uri("/api/v1/movies/{id}/stream-details", heat).with(csrf()))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson()
                .extractingPath("$.detail").asString().startsWith("ffprobe kann nicht gestartet werden");
        JsonNode result = runJob(mockMvc.post().uri("/api/v1/sources/{id}/stream-details", sourceId)
                .with(csrf()).exchange());
        assertThat(result.toString()).contains("\"updated\":0", "\"failed\":1",
                "{\"key\":\"mediaInfo.failure\",\"args\":{\"entry\":\"Heat (1995)\"");
        assertThat(streamsOf("Heat (1995)")).isNull();
        when(probes.current()).thenReturn(probe);
        assertThat(mockMvc.post().uri("/api/v1/movies/{id}/stream-details", heat).with(csrf())).hasStatusOk();
    }

    @Test
    void readsAfterTheOnlineSearchOnlyWhenEnabled() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        givenFile(filme.resolve("Heat (1995)/Heat (1995).mkv"), "");
        Path serien = Files.createDirectories(library.resolve("serien"));
        givenFile(serien.resolve("Lost/Lost - S01E01.mkv"), "");
        givenFile(serien.resolve("Lost/Lost - S01E02.mkv"), "");
        when(probes.probeNewFiles()).thenReturn(false);
        scan(createSource("Filme", filme, "MOVIE"));
        scan(createSource("Serien", serien, "TV_SHOW"));
        long heat = jdbcClient.sql("SELECT id FROM movie").query(Long.class).single();
        long lost = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();

        assertThat(mockMvc.post().uri("/api/v1/movies/{id}/stream-details/after-scrape", heat).with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mockMvc.post().uri("/api/v1/tvshows/{id}/stream-details/after-scrape", lost).with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(streamsOf("Heat")).isNull();

        when(probes.probeOnScrape()).thenReturn(true);
        assertThat(mockMvc.post().uri("/api/v1/movies/{id}/stream-details/after-scrape", heat).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.audios[0].codec").isEqualTo("truehd");
        assertThat(mockMvc.post().uri("/api/v1/tvshows/{id}/stream-details/after-scrape", lost).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.updated").isEqualTo(2);
        assertThat(mockMvc.post().uri("/api/v1/tvshows/{id}/stream-details/after-scrape", lost).with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.updated").isEqualTo(0);
    }

    private String streamsOf(String title) {
        return jdbcClient.sql("SELECT stream_details FROM movie WHERE title = ?").param(title)
                .query(String.class).optional().orElse(null);
    }

    private Path givenFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private long createSource(String name, Path path, String mediaType) {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "path", path.toString(),
                        "mediaType", mediaType)))
                .exchange();
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
