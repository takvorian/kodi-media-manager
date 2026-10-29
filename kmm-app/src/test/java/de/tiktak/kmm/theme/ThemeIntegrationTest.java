package de.tiktak.kmm.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import de.tiktak.kmm.source.MediaSourceController;
import de.tiktak.kmm.tvshow.TvShowController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Titelmusik von Filmen und Serien Ende zu Ende, mit einem Titelmusik-Scraper als Attrappe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class ThemeIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final URI AUDIO = URI.create("http://televisiontunes.com/uploads/audio/Alf.mp3");
    private static final URI PAGE = URI.create("http://televisiontunes.com/Alf_2.html");
    private static final byte[] MP3 = "ID3 Titelmusik".getBytes(StandardCharsets.UTF_8);

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

    private long alfId;
    private long heatId;

    @BeforeEach
    void givenLibraryAndScraper() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien/ALF"));
        Files.writeString(serien.resolve("tvshow.nfo"), "<tvshow><title>ALF</title></tvshow>");
        Files.writeString(serien.resolve("ALF - S01E01.mkv"), "");
        Path filme = Files.createDirectories(library.resolve("filme/Heat"));
        Files.writeString(filme.resolve("Heat.mkv"), "");
        Files.writeString(filme.resolve("Heat.nfo"), "<movie><title>Heat</title><year>1995</year></movie>");
        scan("Serien", library.resolve("serien"), "TV_SHOW");
        scan("Filme", library.resolve("filme"), "MOVIE");
        alfId = read(mockMvc.get().uri(TvShowController.SHOWS_PATH).exchange()).get("content").get(0).get("id")
                .asLong();
        heatId = read(mockMvc.get().uri(MovieController.MOVIES_PATH).exchange()).get("content").get(0).get("id")
                .asLong();

        ThemeScraper scraper = mock(ThemeScraper.class);
        when(scraper.id()).thenReturn("televisiontunes");
        when(scraper.displayName()).thenReturn("TelevisionTunes");
        when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        when(scraper.findThemes(any())).thenAnswer(invocation -> List.of(
                new ThemeCandidate("Main Theme", invocation.<ScrapeRequest>getArgument(0).title(), AUDIO, AUDIO, PAGE, null)));
        doAnswer(invocation -> Files.write(invocation.getArgument(1), MP3)).when(scraper).download(any(), any());
        when(scraperRegistry.themeScrapersFor(any())).thenReturn(List.of(scraper));
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void choosesPlaysAndRemovesTheThemeOfAShow() throws IOException {
        Path theme = library.resolve("serien/ALF/theme.mp3");
        assertThat(mockMvc.get().uri(TvShowThemeController.THEME_PATH, alfId)).hasStatusOk()
                .bodyJson().extractingPath("$.exists").isEqualTo(false);
        assertThat(mockMvc.get().uri(TvShowThemeController.FILE_PATH, alfId)).hasStatus(HttpStatus.NOT_FOUND);

        JsonNode candidates = read(mockMvc.get().uri(TvShowThemeController.CANDIDATES_PATH, alfId).exchange());
        JsonNode first = candidates.get("candidates").get(0);
        assertThat(first.get("sameTitle").asBoolean()).isTrue();
        assertThat(first.get("scraperName").asString()).isEqualTo("TelevisionTunes");

        MvcTestResult applied = mockMvc.put().uri(TvShowThemeController.THEME_PATH, alfId).with(csrf())
                .contentType(JSON).content(first.toString()).exchange();
        assertThat(applied).hasStatusOk().bodyJson().extractingPath("$.exists").isEqualTo(true);
        assertThat(Files.readAllBytes(theme)).isEqualTo(MP3);
        assertThat(mockMvc.get().uri(TvShowThemeController.FILE_PATH, alfId)).hasStatusOk()
                .hasContentType("audio/mpeg").hasBodyTextEqualTo(new String(MP3, StandardCharsets.UTF_8));

        assertThat(mockMvc.delete().uri(TvShowThemeController.THEME_PATH, alfId).with(csrf())).hasStatusOk()
                .bodyJson().extractingPath("$.exists").isEqualTo(false);
        assertThat(theme).doesNotExist();
        assertThat(mockMvc.put().uri(TvShowThemeController.THEME_PATH, alfId).with(csrf()).contentType(JSON)
                .content("{\"scraperId\": \"\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fillsMissingThemesOfMoviesAndShowsAsJobs() throws IOException {
        Files.writeString(library.resolve("serien/ALF/theme.mp3"), "eigene");

        JsonNode shows = awaitJob(mockMvc.post().uri(TvShowThemeController.FILL_PATH).with(csrf()).contentType(JSON)
                .content("{\"showIds\": [%d]}".formatted(alfId)).exchange());
        JsonNode movies = awaitJob(mockMvc.post().uri(MovieThemeController.FILL_PATH).with(csrf()).contentType(JSON)
                .content("{\"movieIds\": [%d, %d]}".formatted(heatId, heatId)).exchange());

        assertThat(shows.get("title").asString()).isEqualTo("Fehlende Titelmusik für 1 Serien holen");
        assertThat(shows.get("result").get("unchanged").asInt()).isEqualTo(1);
        assertThat(Files.readString(library.resolve("serien/ALF/theme.mp3"))).isEqualTo("eigene");
        assertThat(movies.get("result").get("changed").asInt()).as(movies.toString()).isEqualTo(1);
        assertThat(Files.readAllBytes(library.resolve("filme/Heat/theme.mp3"))).isEqualTo(MP3);
        assertThat(mockMvc.get().uri(MovieThemeController.THEME_PATH, heatId)).hasStatusOk()
                .bodyJson().extractingPath("$.exists").isEqualTo(true);
        assertThat(mockMvc.post().uri(MovieThemeController.FILL_PATH).with(csrf()).contentType(JSON)
                .content("{\"movieIds\": []}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private void scan(String name, Path path, String mediaType) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "path", path.toString(),
                        "mediaType", mediaType)))
                .exchange();
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
