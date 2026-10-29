package de.tiktak.kmm.enrichment;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.movie.MovieEnrichmentController;
import de.tiktak.kmm.movie.MovieEnrichmentService;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.source.MediaSourceController;
import de.tiktak.kmm.tvshow.TvShowController;
import de.tiktak.kmm.tvshow.TvShowEnrichmentController;
import de.tiktak.kmm.tvshow.TvShowEnrichmentService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ergänzungen (Bewertungen und leere Felder) Ende zu Ende, mit Trakt und OMDb als Attrappen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class EnrichmentIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Enrichment OMDB = new Enrichment(
            List.of(new ProviderRating("imdb", 8.3, 10, 741503),
                    new ProviderRating("tomatometerallcritics", 88, 100, null)),
            "A group of professional thieves.", 170, LocalDate.of(1995, 12, 15), "R", List.of("Crime"),
            List.of("Michael Mann"), List.of("Michael Mann"), List.of("Al Pacino", "Robert De Niro"),
            List.of("United States"));

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MovieEnrichmentService movieEnrichment;

    @Autowired
    private TvShowEnrichmentService showEnrichment;

    @MockitoBean
    private ScraperRegistry scraperRegistry;

    @TempDir
    Path library;

    @BeforeEach
    void givenLibraryAndScrapers() throws IOException {
        Path heat = Files.createDirectories(library.resolve("filme/Heat"));
        Files.writeString(heat.resolve("Heat.mkv"), "");
        Files.writeString(heat.resolve("Heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><genre>Krimi</genre>
                <uniqueid type="imdb">tt0113277</uniqueid>
                <ratings><rating name="imdb" max="10" default="true"><value>7.0</value><votes>100</votes></rating>
                </ratings></movie>""");
        Path alf = Files.createDirectories(library.resolve("serien/ALF"));
        Files.writeString(alf.resolve("tvshow.nfo"), """
                <tvshow><title>ALF</title><plot>Ein Außerirdischer.</plot>
                <uniqueid type="imdb">tt0090390</uniqueid></tvshow>""");
        Files.writeString(alf.resolve("ALF - S01E01.mkv"), "");
        scan("Filme", library.resolve("filme"), "MOVIE");
        scan("Serien", library.resolve("serien"), "TV_SHOW");

        EnrichmentScraper trakt = scraper("trakt", Set.of("trakt"), false,
                Enrichment.ofRatings(List.of(new ProviderRating("trakt", 8.2, 10, 41234))));
        EnrichmentScraper omdb = scraper("omdb", Set.of("imdb", "tomatometerallcritics", "metacritic"), true, OMDB);
        when(scraperRegistry.enrichmentScrapersFor(any(MediaType.class))).thenReturn(List.of(trakt, omdb));
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void refreshesRatingsAndFillsEmptyFieldsOfAMovie() throws IOException {
        long heatId = firstId(MovieController.MOVIES_PATH);

        JsonNode job = awaitJob(post(MovieEnrichmentController.REFRESH_PATH, "{\"movieIds\": [%d]}".formatted(heatId)));

        assertThat(job.get("title").asString()).isEqualTo("Bewertungen für 1 Filme aktualisieren");
        assertThat(job.get("result").get("changed").asInt()).as(job.toString()).isEqualTo(1);
        String nfo = Files.readString(library.resolve("filme/Heat/Heat.nfo"));
        assertThat(nfo).contains("<plot>A group of professional thieves.</plot>", "<runtime>170</runtime>",
                "<genre>Krimi</genre>", "<director>Michael Mann</director>", "<credits>Michael Mann</credits>",
                "<name>Al Pacino</name>", "<country>United States</country>", "<mpaa>R</mpaa>",
                "<value>8.3</value>", "<votes>741503</votes>", "name=\"trakt\"", "name=\"tomatometerallcritics\"")
                .doesNotContain("<genre>Crime</genre>", "<value>7.0</value>");
        assertThat(nfo.indexOf("name=\"imdb\"")).as("bleibt Standard").isLessThan(nfo.indexOf("name=\"trakt\""));
        assertThat(nfo).contains("default=\"true\" max=\"10\" name=\"imdb\"");

        JsonNode again = awaitJob(post(MovieEnrichmentController.REFRESH_PATH,
                "{\"movieIds\": [%d]}".formatted(heatId)));
        assertThat(again.get("result").get("unchanged").asInt()).isEqualTo(1);
        assertThat(movieEnrichment.fillMissing(heatId)).isFalse();
    }

    @Test
    void fillsMissingRatingsAndFieldsOfAShowButKeepsItsOwnValues() throws IOException {
        long alfId = firstId(TvShowController.SHOWS_PATH);

        assertThat(showEnrichment.fillMissing(alfId)).isTrue();

        String nfo = Files.readString(library.resolve("serien/ALF/tvshow.nfo"));
        assertThat(nfo).contains("<plot>Ein Außerirdischer.</plot>", "<premiered>1995-12-15</premiered>",
                "<genre>Crime</genre>", "<name>Robert De Niro</name>",
                "default=\"true\" max=\"10\" name=\"trakt\"", "name=\"imdb\"");
        assertThat(showEnrichment.fillMissing(alfId)).isFalse();
        assertThat(post(TvShowEnrichmentController.REFRESH_PATH, "{\"showIds\": []}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static EnrichmentScraper scraper(String id, Set<String> sources, boolean details, Enrichment result) {
        EnrichmentScraper scraper = mock(EnrichmentScraper.class);
        when(scraper.id()).thenReturn(id);
        when(scraper.ratingSources()).thenReturn(sources);
        when(scraper.providesDetails()).thenReturn(details);
        when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        when(scraper.findEnrichment(any())).thenReturn(Optional.of(result));
        return scraper;
    }

    private long firstId(String path) {
        return read(mockMvc.get().uri(path).exchange()).get("content").get(0).get("id").asLong();
    }

    private MvcTestResult post(String path, String body) {
        return mockMvc.post().uri(path).with(csrf()).contentType(JSON).content(body).exchange();
    }

    private void scan(String name, Path path, String mediaType) {
        MvcTestResult source = post(MediaSourceController.BASE_PATH, objectMapper.writeValueAsString(Map.of(
                "name", name, "path", path.toString(), "mediaType", mediaType)));
        assertThat(source).hasStatus(HttpStatus.CREATED);
        awaitJob(mockMvc.post().uri(LibraryScanController.SCAN_PATH, read(source).get("id").asLong()).with(csrf())
                .exchange());
    }

    private JsonNode awaitJob(MvcTestResult started) {
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job;
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
