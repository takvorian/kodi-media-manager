package de.tiktak.kmm.rating;

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
import java.util.Optional;

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
import de.tiktak.kmm.movie.MovieRatingController;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.RatingScraper;
import de.tiktak.kmm.source.MediaSourceController;
import de.tiktak.kmm.tvshow.TvShowController;
import de.tiktak.kmm.tvshow.TvShowRatingController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Fehlende Bewertungen weiterer Anbieter Ende zu Ende, mit einem Bewertungs-Scraper als Attrappe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class RatingIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

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

    @BeforeEach
    void givenLibraryAndScraper() throws IOException {
        Path heat = Files.createDirectories(library.resolve("filme/Heat"));
        Files.writeString(heat.resolve("Heat.mkv"), "");
        Files.writeString(heat.resolve("Heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><uniqueid type="imdb">tt0113277</uniqueid>
                <ratings><rating name="themoviedb" max="10" default="true"><value>7.9</value><votes>6000</votes>
                </rating></ratings></movie>""");
        Path alf = Files.createDirectories(library.resolve("serien/ALF"));
        Files.writeString(alf.resolve("tvshow.nfo"),
                "<tvshow><title>ALF</title><uniqueid type=\"tvdb\">78020</uniqueid></tvshow>");
        Files.writeString(alf.resolve("ALF - S01E01.mkv"), "");
        scan("Filme", library.resolve("filme"), "MOVIE");
        scan("Serien", library.resolve("serien"), "TV_SHOW");

        RatingScraper trakt = mock(RatingScraper.class);
        when(trakt.id()).thenReturn("trakt");
        when(trakt.ratingSource()).thenReturn("trakt");
        when(trakt.missingConfiguration()).thenReturn(Optional.empty());
        when(trakt.findRating(any())).thenReturn(Optional.of(new ProviderRating("trakt", 8.2, 10, 41234)));
        when(scraperRegistry.ratingScrapersFor(any(MediaType.class))).thenReturn(List.of(trakt));
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void addsTheMissingRatingToMoviesAndShowsOnce() throws IOException {
        long heatId = firstId(MovieController.MOVIES_PATH);
        long alfId = firstId(TvShowController.SHOWS_PATH);

        JsonNode movies = awaitJob(post(MovieRatingController.FILL_PATH, "{\"movieIds\": [%d]}".formatted(heatId)));
        JsonNode shows = awaitJob(post(TvShowRatingController.FILL_PATH, "{\"showIds\": [%d]}".formatted(alfId)));

        assertThat(movies.get("title").asString()).isEqualTo("Fehlende Bewertungen für 1 Filme holen");
        assertThat(movies.get("result").get("changed").asInt()).as(movies.toString()).isEqualTo(1);
        assertThat(shows.get("result").get("changed").asInt()).as(shows.toString()).isEqualTo(1);
        assertThat(Files.readString(library.resolve("filme/Heat/Heat.nfo")))
                .contains("name=\"themoviedb\"", "name=\"trakt\"", "<value>8.2</value>", "<votes>41234</votes>");
        assertThat(Files.readString(library.resolve("serien/ALF/tvshow.nfo")))
                .contains("default=\"true\" max=\"10\" name=\"trakt\"", "<value>8.2</value>");

        JsonNode again = awaitJob(post(MovieRatingController.FILL_PATH, "{\"movieIds\": [%d]}".formatted(heatId)));
        assertThat(again.get("result").get("unchanged").asInt()).isEqualTo(1);
        assertThat(post(TvShowRatingController.FILL_PATH, "{\"showIds\": []}")).hasStatus(HttpStatus.BAD_REQUEST);
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
