package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sun.net.httpserver.HttpServer;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bilder von Serien, Staffeln und Episoden über die REST-API mit Stub-Scrapern; die Bilddateien liefert
 * ein lokaler HTTP-Server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvShowArtworkIntegrationTest {

    private static final byte[] POSTER = {(byte) 0xFF, (byte) 0xD8, 1};
    private static final byte[] SEASON_POSTER = {(byte) 0xFF, (byte) 0xD8, 2};
    private static final byte[] THUMB = {(byte) 0xFF, (byte) 0xD8, 3};
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(10);

    private static HttpServer server;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private ScraperRegistry scraperRegistry;

    @TempDir
    Path library;

    private long showId;
    private long episodeId;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Map<String, byte[]> images = Map.of("/poster.jpg", POSTER, "/season1.jpg", SEASON_POSTER, "/thumb.jpg", THUMB);
        server.createContext("/", exchange -> {
            byte[] body = images.getOrDefault(exchange.getRequestURI().getPath(), new byte[0]);
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void createShowAndScrapers() throws IOException {
        Path episode = library.resolve("Lost/Staffel 1/Lost - S01E01.mkv");
        Files.createDirectories(episode.getParent());
        Files.createFile(episode);
        scan(createSource());
        showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();
        episodeId = jdbcClient.sql("SELECT id FROM episode").query(Long.class).single();
        jdbcClient.sql("UPDATE tv_show SET tvdb_id = '73739'").update();

        ImageScraper images = mock(ImageScraper.class);
        when(images.id()).thenReturn("tvdb");
        when(images.displayName()).thenReturn("TheTVDB");
        when(images.findImages(any())).thenReturn(List.of(
                ImageCandidate.builder().type(ImageType.POSTER).url(url("/poster.jpg")).language("de").build(),
                ImageCandidate.builder().type(ImageType.POSTER).url(url("/season1.jpg")).season(1).build()));
        when(scraperRegistry.imageScrapersFor(de.tiktak.kmm.plugin.api.MediaType.TV_SHOW)).thenReturn(List.of(images));

        TvShowScraper metadata = mock(TvShowScraper.class);
        when(metadata.id()).thenReturn("tvdb");
        when(metadata.displayName()).thenReturn("TheTVDB");
        when(metadata.supportedMediaTypes()).thenReturn(Set.of(de.tiktak.kmm.plugin.api.MediaType.TV_SHOW));
        when(metadata.fetch("73739")).thenReturn(Optional.of(TvShowResult.builder().title("Lost")
                .episodes(List.of(EpisodeResult.builder().season(1).episode(1).thumb(url("/thumb.jpg")).build()))
                .build()));
        TvShowScraper unconfigured = mock(TvShowScraper.class);
        when(unconfigured.missingConfiguration())
                .thenReturn(Optional.of(LocalizedText.literal("Für TMDB fehlt ein API-Key.")));
        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(metadata, unconfigured));
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void listsShowAndSeasonArtwork() {
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_ARTWORK_PATH, showId)).hasStatusOk().bodyJson()
                .extractingPath("$[*].type").asArray()
                .containsExactly("POSTER", "FANART", "BANNER", "CLEARLOGO", "CLEARART", "LANDSCAPE", "CHARACTERART");
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_ARTWORK_PATH, showId).param("season", "1"))
                .hasStatusOk().bodyJson()
                .extractingPath("$[*].type").asArray().containsExactly("POSTER", "BANNER", "FANART", "LANDSCAPE");
    }

    @Test
    void separatesShowAndSeasonCandidates() {
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_CANDIDATES_PATH, showId, "POSTER"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"candidates": [{"scraperId": "tvdb", "language": "de", "season": null}], "warnings": []}""");
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_CANDIDATES_PATH, showId, "POSTER")
                .param("season", "1"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"candidates": [{"scraperId": "tvdb", "season": 1}], "warnings": []}""");
    }

    @Test
    void appliesServesAndRemovesShowAndSeasonImages() throws IOException {
        assertThat(put(TvShowArtworkController.SHOW_TYPE_PATH, showId, "POSTER", "/poster.jpg", null))
                .hasStatusOk().bodyJson().extractingPath("$.exists").isEqualTo(true);
        assertThat(put(TvShowArtworkController.SHOW_TYPE_PATH, showId, "POSTER", "/season1.jpg", "1")).hasStatusOk();

        assertThat(Files.readAllBytes(library.resolve("Lost/poster.jpg"))).isEqualTo(POSTER);
        assertThat(Files.readAllBytes(library.resolve("Lost/season01-poster.jpg"))).isEqualTo(SEASON_POSTER);
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_IMAGE_PATH, showId, "POSTER").param("season", "1"))
                .hasStatusOk().hasContentType(MediaType.IMAGE_JPEG);

        assertThat(mockMvc.delete().uri(TvShowArtworkController.SHOW_TYPE_PATH, showId, "POSTER").param("season", "1")
                .with(csrf())).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(library.resolve("Lost/season01-poster.jpg")).doesNotExist();
        assertThat(library.resolve("Lost/poster.jpg")).exists();
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_IMAGE_PATH, showId, "POSTER").param("season", "1"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void appliesEpisodeThumbsFromTheTvShowScrapers() throws IOException {
        assertThat(mockMvc.get().uri(TvShowArtworkController.EPISODE_ARTWORK_PATH, episodeId)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("[{\"type\": \"THUMB\", \"exists\": false}]");
        assertThat(mockMvc.get().uri(TvShowArtworkController.EPISODE_CANDIDATES_PATH, episodeId, "THUMB"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"candidates": [{"scraperId": "tvdb", "scraperName": "TheTVDB", "type": "THUMB"}],
                         "warnings": ["Für TMDB fehlt ein API-Key."]}""");

        assertThat(put(TvShowArtworkController.EPISODE_TYPE_PATH, episodeId, "THUMB", "/thumb.jpg", null))
                .hasStatusOk();

        assertThat(Files.readAllBytes(library.resolve("Lost/Staffel 1/Lost - S01E01-thumb.jpg"))).isEqualTo(THUMB);
        assertThat(mockMvc.get().uri(TvShowArtworkController.EPISODE_IMAGE_PATH, episodeId, "THUMB")).hasStatusOk();
        assertThat(mockMvc.delete().uri(TvShowArtworkController.EPISODE_TYPE_PATH, episodeId, "THUMB").with(csrf()))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mockMvc.get().uri(TvShowArtworkController.EPISODE_IMAGE_PATH, episodeId, "THUMB"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsUnsupportedTypesAndShowsWithoutIds() {
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_CANDIDATES_PATH, showId, "DISCART"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_CANDIDATES_PATH, showId, "CHARACTERART")
                .param("season", "1")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.get().uri(TvShowArtworkController.EPISODE_CANDIDATES_PATH, episodeId, "POSTER"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        jdbcClient.sql("UPDATE tv_show SET tvdb_id = NULL").update();
        assertThat(mockMvc.get().uri(TvShowArtworkController.SHOW_CANDIDATES_PATH, showId, "POSTER"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").asString().contains("TVDB-, TMDB- oder IMDb-ID");
    }

    private MvcTestResult put(String path, long id, String type, String image, String season) {
        var request = mockMvc.put().uri(path, id, type).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"" + url(image) + "\"}");
        return (season == null ? request : request.param("season", season)).exchange();
    }

    private static URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private long createSource() {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("name", "Serien", "path", library.toString(), "mediaType", "TV_SHOW")))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private void scan(long sourceId) {
        MvcTestResult started = mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange();
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
