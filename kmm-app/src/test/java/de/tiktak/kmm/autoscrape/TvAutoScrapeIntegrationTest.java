package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.awaitility.Awaitility;
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

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.TrailerCandidate;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Automatisches Scrapen einer Serienquelle über die REST-API mit Stub-Scrapern: Zuordnung, Episoden
 * und Bilder. Die Bilddateien liefert ein lokaler HTTP-Server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvAutoScrapeIntegrationTest {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, 7};
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(20);
    private static final String LOST_TRAILER = "plugin://plugin.video.youtube/?action=play_video&videoid=KTu8iDynwNc";

    private static HttpServer server;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SettingsService settingsService;

    @MockitoBean
    private ScraperRegistry scraperRegistry;

    @MockitoBean
    private StreamDetailsService streamDetails;

    @TempDir
    Path library;

    private long sourceId;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, IMAGE.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(IMAGE);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void createLibraryAndScrapers() throws IOException {
        for (String file : List.of("Lost (2004)/Staffel 1/Lost - S01E01.mkv", "Unbekannt/Staffel 1/x - S01E01.mkv")) {
            Path episode = library.resolve(file);
            Files.createDirectories(episode.getParent());
            Files.createFile(episode);
        }
        sourceId = createSource();
        await(post(LibraryScanController.SCAN_PATH.replace("{sourceId}", String.valueOf(sourceId))));

        TvShowScraper tvdb = mock(TvShowScraper.class);
        when(tvdb.id()).thenReturn("tvdb");
        when(tvdb.displayName()).thenReturn("TheTVDB");
        when(tvdb.supportedMediaTypes()).thenReturn(Set.of(de.tiktak.kmm.plugin.api.MediaType.TV_SHOW));
        TvShowResult lost = TvShowResult.builder().title("Lost").premiered(LocalDate.of(2004, 9, 22))
                .uniqueIds(Map.of("tvdb", "73739"))
                .episodes(List.of(EpisodeResult.builder().season(1).episode(1).title("Gestrandet")
                                .thumb(url("/thumb.jpg")).build(),
                        EpisodeResult.builder().season(2).episode(1).title("Die Luke").plot("Locke öffnet die Luke.")
                                .thumb(url("/thumb2.jpg")).build()))
                .build();
        when(tvdb.scrape(any())).thenAnswer(invocation -> {
            ScrapeRequest request = invocation.getArgument(0);
            return request.title().equals("Lost") ? Optional.of(lost) : Optional.empty();
        });
        when(tvdb.fetch("73739")).thenReturn(Optional.of(lost));
        when(tvdb.episodeDetails(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(tvdb));

        ImageScraper images = mock(ImageScraper.class);
        when(images.id()).thenReturn("tvdb");
        when(images.displayName()).thenReturn("TheTVDB");
        when(images.findImages(any())).thenReturn(List.of(
                ImageCandidate.builder().type(ImageType.POSTER).url(url("/poster.jpg")).build(),
                ImageCandidate.builder().type(ImageType.POSTER).url(url("/season.jpg")).season(1).build(),
                ImageCandidate.builder().type(ImageType.POSTER).url(url("/season2.jpg")).season(2).build()));
        when(scraperRegistry.imageScrapersFor(de.tiktak.kmm.plugin.api.MediaType.TV_SHOW)).thenReturn(List.of(images));
        TrailerScraper trailers = mock(TrailerScraper.class);
        when(trailers.displayName()).thenReturn("TMDB");
        when(trailers.findTrailers(any())).thenReturn(List.of(new TrailerCandidate("Lost Trailer", LOST_TRAILER,
                URI.create("https://www.youtube.com/watch?v=KTu8iDynwNc"), null, "en", "Trailer", true, 480, null)));
        when(scraperRegistry.trailerScrapersFor(de.tiktak.kmm.plugin.api.MediaType.TV_SHOW))
                .thenReturn(List.of(trailers));
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
        settingsService.update(settingsService.current().withRename(RenameSettings.DEFAULTS));
    }

    @Test
    void scrapesOnlyTheChosenSeasonAndRenamesOnlyItsEpisodes() throws IOException {
        givenSecondSeason();
        RenameSettings defaults = RenameSettings.DEFAULTS;
        settingsService.update(settingsService.current().withRename(new RenameSettings(defaults.movieFolder(),
                defaults.movieFile(), "Serie {show}", "Staffel {season}", "{show} S{season:2}E{episode:2}",
                false, false, false, true, false)));

        JsonNode result = await(post(seasonPath(showIdOf("Lost"), 2, false)));

        assertThat(result.get("scraped").asInt()).as("noch nicht zugeordnete Serie").isEqualTo(1);
        assertThat(result.get("episodesUpdated").asInt()).isEqualTo(1);
        assertThat(result.get("trailersSaved").asInt()).isZero();
        assertThat(result.get("failures")).isEmpty();
        Path show = library.resolve("Lost (2004)");
        assertThat(Files.readString(show.resolve("Staffel 2/Lost S02E01.nfo"))).contains("<title>Die Luke</title>");
        assertThat(show.resolve("Staffel 2/Lost S02E01-thumb.jpg")).exists();
        assertThat(show.resolve("season02-poster.jpg")).exists();
        assertThat(show.resolve("season01-poster.jpg")).as("andere Staffel").doesNotExist();
        assertThat(show.resolve("poster.jpg")).as("Bild der Serie").doesNotExist();
        assertThat(show.resolve("Staffel 1/Lost - S01E01.mkv")).as("andere Staffel").exists();
        assertThat(show.resolve("Staffel 1/Lost - S01E01.nfo")).doesNotExist();
        assertThat(library.resolve("Serie Lost")).as("Serienordner bleibt").doesNotExist();
        assertThat(Files.readString(show.resolve("tvshow.nfo"))).doesNotContain("<trailer>");
    }

    @Test
    void refetchesASeasonAndReplacesHandEditedTexts() throws IOException {
        long showId = showIdOf("Lost");
        await(post(AutoScrapeController.TV_SHOW_PATH.replace("{showId}", String.valueOf(showId))));
        Path nfo = library.resolve("Lost (2004)/Staffel 1/Lost - S01E01.nfo");
        Files.writeString(nfo, Files.readString(nfo).replace("<title>Gestrandet</title>", "<title>Von Hand</title>"));

        assertThat(await(post(seasonPath(showId, 1, false))).get("episodesUpdated").asInt())
                .as("Lücken füllen behält den Titel").isZero();
        assertThat(Files.readString(nfo)).contains("<title>Von Hand</title>");

        JsonNode result = await(post(seasonPath(showId, 1, true)));

        assertThat(result.get("episodesUpdated").asInt()).isEqualTo(1);
        assertThat(Files.readString(nfo)).contains("<title>Gestrandet</title>");
    }

    @Test
    void rejectsSeasonsWithoutEpisodesAndRefetchingUnidentifiedShows() {
        long showId = showIdOf("Lost");
        assertThat(mockMvc.post().uri(seasonPath(showId, 7, false)).with(csrf()).exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail")
                .isEqualTo("„Lost“ hat keine Episoden in Staffel 7.");
        assertThat(mockMvc.post().uri(seasonPath(showId, -1, false)).with(csrf()).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);

        JsonNode result = await(post(seasonPath(showId, 1, true)));

        assertThat(result.get("failures").get(0).asString()).startsWith("Lost: „Lost“ ist bei TheTVDB nicht");
    }

    private void givenSecondSeason() throws IOException {
        Path episode = library.resolve("Lost (2004)/Staffel 2/Lost - S02E01.mkv");
        Files.createDirectories(episode.getParent());
        Files.createFile(episode);
        await(post(LibraryScanController.SCAN_PATH.replace("{sourceId}", String.valueOf(sourceId))));
    }

    private static String seasonPath(long showId, int season, boolean overwrite) {
        return AutoScrapeController.TV_SEASON_PATH.replace("{showId}", String.valueOf(showId))
                .replace("{season}", String.valueOf(season)) + "?overwrite=" + overwrite;
    }

    @Test
    void scrapesOnlyTheShowItWasAskedFor() {
        JsonNode result = await(post(AutoScrapeController.TV_SHOW_PATH.replace("{showId}",
                String.valueOf(showIdOf("Lost")))));

        assertThat(result.get("shows").asInt()).isEqualTo(1);
        assertThat(library.resolve("Lost (2004)/poster.jpg")).exists();
        assertThat(library.resolve("Unbekannt/poster.jpg")).doesNotExist();
    }

    @Test
    void identifiesShowsFillsEpisodesAndFetchesMissingImages() throws IOException {
        JsonNode result = await(post(AutoScrapeController.AUTO_SCRAPE_PATH.replace("{sourceId}",
                String.valueOf(sourceId))));

        assertThat(result.toString()).isEqualTo("{\"shows\":2,\"scraped\":1,\"unmatched\":1,\"episodesUpdated\":1,"
                + "\"imagesSaved\":3,\"trailersSaved\":1,\"themesSaved\":0,\"enriched\":0,\"unmatchedEntries\":[{\"type\":\"TV_SHOW\",\"id\":" + showIdOf("Unbekannt") + ",\"label\":\"Unbekannt\"}],\"failures\":[],"
                + "\"warnings\":[]}");
        assertThat(jdbcClient.sql("SELECT tvdb_id FROM tv_show WHERE title = 'Lost'").query(String.class).single())
                .isEqualTo("73739");
        assertThat(Files.readString(library.resolve("Lost (2004)/tvshow.nfo"))).contains("73739",
                "<trailer>" + LOST_TRAILER.replace("&", "&amp;") + "</trailer>");
        assertThat(Files.readString(library.resolve("Lost (2004)/Staffel 1/Lost - S01E01.nfo")))
                .contains("<title>Gestrandet</title>");
        assertThat(library.resolve("Lost (2004)/poster.jpg")).exists();
        assertThat(library.resolve("Lost (2004)/season01-poster.jpg")).exists();
        assertThat(library.resolve("Lost (2004)/Staffel 1/Lost - S01E01-thumb.jpg")).exists();

        JsonNode repeated = await(post(AutoScrapeController.AUTO_SCRAPE_PATH.replace("{sourceId}",
                String.valueOf(sourceId))));
        assertThat(repeated.get("scraped").asInt()).as("schon zugeordnet").isZero();
        assertThat(repeated.get("imagesSaved").asInt()).as("vorhandene Bilder bleiben").isZero();
    }

    private long showIdOf(String title) {
        return jdbcClient.sql("SELECT id FROM tv_show WHERE title = ?").param(title).query(Long.class).single();
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

    private String post(String path) {
        MvcTestResult result = mockMvc.post().uri(path).with(csrf()).exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        return read(result).get("id").asString();
    }

    private JsonNode await(String jobId) {
        JsonNode job = Awaitility.await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
