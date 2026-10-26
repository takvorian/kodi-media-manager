package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Online-Suche für Serien über die REST-API mit einem Stub-Scraper: Suche, Vorschlag, Übernahme der
 * Episodendaten in Datenbank und NFOs sowie fehlende Episoden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvShowScrapeIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(10);
    private static final String SCRAPER = "stub";
    /** Jeder Test nutzt eine eigene ID, weil geladene Serien zwischengespeichert werden. */
    private static final AtomicInteger PROVIDER_IDS = new AtomicInteger(4600);

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

    private final TvShowScraper scraper = mock(TvShowScraper.class);
    private String providerId;
    private long showId;

    @BeforeEach
    void createShowAndScraper() throws IOException {
        providerId = String.valueOf(PROVIDER_IDS.incrementAndGet());
        createFile("Lost/Staffel 1/Lost - S01E01E02 - Pilot.mkv");
        createFile("Lost/Staffel 1/Lost - S01E03.mkv");
        Files.writeString(library.resolve("Lost/Staffel 1/Lost - S01E03.nfo"), """
                <episodedetails><title>Tabula Rasa (NFO)</title><season>1</season><episode>3</episode>
                <plot>Eigene Handlung.</plot></episodedetails>""");
        createFile("Lost/Staffel 1/Lost - S01E09.mkv");
        scan(createSource());
        showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();
        when(scraper.id()).thenReturn(SCRAPER);
        when(scraper.displayName()).thenReturn("Stub");
        when(scraper.supportedMediaTypes()).thenReturn(Set.of(MediaType.TV_SHOW));
        when(scraper.fetch(providerId)).thenReturn(Optional.of(lost()));
        when(scraper.episodeDetails(any())).thenAnswer(invocation -> {
            EpisodeResult episode = invocation.getArgument(0);
            return episode.episode() == 2
                    ? EpisodeResult.builder().season(1).episode(2).title(episode.title())
                            .uniqueIds(episode.uniqueIds()).directors(List.of("Jack Bender"))
                            .writers(List.of("Damon Lindelof")).build()
                    : episode;
        });
        when(scraperRegistry.tvShowScraper(SCRAPER)).thenReturn(Optional.of(scraper));
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void searchesWithTheShowTitleOrACustomTitle() {
        when(scraper.search(any())).thenReturn(List.of(new SearchResult(providerId, "Lost", "Lost", 2004, "Absturz",
                URI.create("https://example.org/lost.jpg"))));

        assertThat(mockMvc.get().uri(TvShowScrapeController.SEARCH_PATH, showId).param("scraper", SCRAPER))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"providerId": "%s", "title": "Lost", "year": 2004,
                          "posterUrl": "https://example.org/lost.jpg"}]""".formatted(providerId));
        assertThat(mockMvc.get().uri(TvShowScrapeController.SEARCH_PATH, showId).param("scraper", SCRAPER)
                .param("title", "Verschollen").param("year", "2005")).hasStatusOk();
        verify(scraper).search(new SearchQuery(MediaType.TV_SHOW, "Lost", null));
        verify(scraper).search(new SearchQuery(MediaType.TV_SHOW, "Verschollen", 2005));
    }

    @Test
    void proposesShowMetadataWithEpisodeCounts() {
        assertThat(mockMvc.get().uri(TvShowScrapeController.PROPOSAL_PATH, showId).param("scraper", SCRAPER)
                .param("providerId", providerId))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"metadata": {"title": "Lost", "year": 2004, "status": "Ended", "mpaa": "FSK 16",
                                      "genres": ["Drama"], "studios": ["ABC"], "tvdbId": "73739", "imdbId": "tt0411008"},
                         "matchedEpisodes": 3, "providerEpisodes": 5}""");
    }

    @Test
    void fillsEmptyEpisodeFieldsAndWritesTheNfos() throws IOException {
        JsonNode result = fill();

        assertThat(result.toString()).isEqualTo(
                "{\"updated\":3,\"unchanged\":0,\"unmatched\":1,\"failedFiles\":[]}");
        String multiNfo = Files.readString(library.resolve("Lost/Staffel 1/Lost - S01E01E02 - Pilot.nfo"));
        assertThat(multiNfo).contains("<title>Gestrandet (1)</title>", "<title>Gestrandet (2)</title>",
                "type=\"tvdb\">127131</uniqueid>", "<director>J. J. Abrams</director>", "<name>Greg Grunberg</name>");
        String ownNfo = Files.readString(library.resolve("Lost/Staffel 1/Lost - S01E03.nfo"));
        assertThat(ownNfo).contains("<title>Tabula Rasa (NFO)</title>", "<plot>Eigene Handlung.</plot>",
                "<aired>2004-10-06</aired>");
        assertThat(Files.readString(library.resolve("Lost/tvshow.nfo")))
                .contains("<name>Matthew Fox</name>", "<namedseason number=\"1\">Die Insel</namedseason>");
        long pilotId = jdbcClient.sql("SELECT id FROM episode WHERE episode_number = 1").query(Long.class).single();
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, pilotId)).bodyJson().isLenientlyEqualTo("""
                {"metadata": {"title": "Gestrandet (1)", "aired": "2004-09-22", "tvdbId": "127131"},
                 "extras": {"ratings": [{"source": "tvdb", "value": 8.1}]}}""");

        assertThat(multiNfo).contains("<director>Jack Bender</director>", "<credits>Damon Lindelof</credits>");
        verify(scraper, never()).episodeDetails(argThat(episode -> episode.episode() == 1));
        verify(scraper).episodeDetails(argThat(episode -> episode.episode() == 2));
        verify(scraper).episodeDetails(argThat(episode -> episode.episode() == 3));

        assertThat(fill().toString()).as("zweite Übernahme ändert nichts").isEqualTo(
                "{\"updated\":0,\"unchanged\":3,\"unmatched\":1,\"failedFiles\":[]}");
        verify(scraper, times(1)).fetch(providerId);
        verify(scraper).episodeDetails(argThat(episode -> episode.episode() == 2));
    }

    @Test
    void listsMissingEpisodesOfTheIdentifiedShow() {
        when(scraper.scrape(any())).thenReturn(Optional.of(lost()));

        assertThat(mockMvc.get().uri(TvShowScrapeController.MISSING_EPISODES_PATH, showId).param("scraper", SCRAPER))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"season": 0, "episode": 1, "title": "Die Reise", "aired": "2005-04-27"},
                         {"season": 1, "episode": 4, "title": "Wildschwein", "aired": null}]""");
    }

    @Test
    void usesTheKnownProviderIdForMissingEpisodes() {
        jdbcClient.sql("UPDATE tv_show SET tvdb_id = :id").param("id", providerId).update();
        when(scraper.id()).thenReturn("tvdb");
        when(scraperRegistry.tvShowScraper("tvdb")).thenReturn(Optional.of(scraper));

        assertThat(mockMvc.get().uri(TvShowScrapeController.MISSING_EPISODES_PATH, showId).param("scraper", "tvdb"))
                .hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(2);
        verify(scraper).fetch(providerId);
    }

    @Test
    void reportsShowsThatCannotBeIdentified() {
        when(scraper.scrape(any())).thenReturn(Optional.empty());

        assertThat(mockMvc.get().uri(TvShowScrapeController.MISSING_EPISODES_PATH, showId).param("scraper", SCRAPER))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").asString().contains("nicht eindeutig zugeordnet");
    }

    @Test
    void reportsScraperProblems() {
        TvShowScraper unconfigured = mock(TvShowScraper.class);
        when(unconfigured.displayName()).thenReturn("TheTVDB");
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of("Für TVDB fehlt ein API-Key."));
        when(scraperRegistry.tvShowScraper("tvdb")).thenReturn(Optional.of(unconfigured));
        when(scraper.fetch("404")).thenReturn(Optional.empty());
        when(scraper.fetch("502")).thenThrow(new ScraperException("Anbieter nicht erreichbar"));

        assertThat(proposal("unbekannt", providerId)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(proposal("tvdb", providerId)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Für TVDB fehlt ein API-Key.");
        assertThat(proposal(SCRAPER, "404")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(proposal(SCRAPER, "502")).hasStatus(HttpStatus.BAD_GATEWAY)
                .bodyJson().extractingPath("$.detail").isEqualTo("Anbieter nicht erreichbar");
        assertThat(mockMvc.get().uri(TvShowScrapeController.SEARCH_PATH, 999_999).param("scraper", SCRAPER))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult proposal(String scraperId, String id) {
        return mockMvc.get().uri(TvShowScrapeController.PROPOSAL_PATH, showId).param("scraper", scraperId)
                .param("providerId", id).exchange();
    }

    private JsonNode fill() {
        MvcTestResult result = mockMvc.post().uri(TvShowScrapeController.EPISODES_PATH, showId).with(csrf())
                .param("scraper", SCRAPER).param("providerId", providerId).exchange();
        assertThat(result).hasStatusOk();
        return read(result);
    }

    private static TvShowResult lost() {
        return TvShowResult.builder()
                .title("Lost").originalTitle("Lost").year(2004).premiered(LocalDate.of(2004, 9, 22))
                .plot("Überlebende eines Flugzeugabsturzes.").runtime(45).certification("FSK 16").status("Ended")
                .genres(List.of("Drama")).studios(List.of("ABC"))
                .actors(List.of(new CastMember("Matthew Fox", "Jack Shephard", null)))
                .ratings(List.of(new ProviderRating("tvdb", 8.4, 10, 900)))
                .uniqueIds(Map.of("tvdb", "73739", "imdb", "tt0411008"))
                .seasons(List.of(new SeasonResult(0, null, null, null), new SeasonResult(1, "Die Insel", null, null)))
                .episodes(List.of(
                        EpisodeResult.builder().season(0).episode(1).title("Die Reise")
                                .aired(LocalDate.of(2005, 4, 27)).build(),
                        EpisodeResult.builder().season(1).episode(1).title("Gestrandet (1)").plot("Der Absturz.")
                                .aired(LocalDate.of(2004, 9, 22)).runtime(42).directors(List.of("J. J. Abrams"))
                                .guestStars(List.of(new CastMember("Greg Grunberg", "Pilot", null)))
                                .ratings(List.of(new ProviderRating("tvdb", 8.1, 10, 120)))
                                .uniqueIds(Map.of("tvdb", "127131")).build(),
                        EpisodeResult.builder().season(1).episode(2).title("Gestrandet (2)")
                                .uniqueIds(Map.of("tvdb", "127132")).build(),
                        EpisodeResult.builder().season(1).episode(3).title("Tabula Rasa").plot("Kate wird verhört.")
                                .aired(LocalDate.of(2004, 10, 6)).build(),
                        EpisodeResult.builder().season(1).episode(4).title("Wildschwein").build()))
                .build();
    }

    private long createSource() {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("name", "Serien", "path", library.toString(), "mediaType", "TV_SHOW")))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private void scan(long sourceId) {
        MvcTestResult started = mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf())
                .exchange();
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void createFile(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }
}
