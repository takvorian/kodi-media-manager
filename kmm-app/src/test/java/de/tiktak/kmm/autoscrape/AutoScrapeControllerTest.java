package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = AutoScrapeController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class AutoScrapeControllerTest {

    private static final UUID JOB_ID = UUID.fromString("7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f01");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private AutoScrapeService autoScrapeService;

    @Test
    void startsAutoScrapeAsJob() {
        when(autoScrapeService.start(3L)).thenReturn(new JobSnapshot(JOB_ID, "auto-scrape:3", "Scrapen „Filme“",
                JobStatus.RUNNING, 0, 0, null, null, Instant.parse("2026-10-25T12:00:00Z"), null));

        assertThat(mockMvc.post().uri(AutoScrapeController.AUTO_SCRAPE_PATH, 3L).with(csrf()))
                .hasStatus(202)
                .hasHeader("Location", "/api/v1/jobs/" + JOB_ID)
                .bodyJson()
                .extractingPath("$.key").isEqualTo("auto-scrape:3");
    }

    @Test
    void startsTheScrapeOfASingleMovie() {
        when(autoScrapeService.startMovie(5L)).thenReturn(job("movie-scrape:5"));

        assertThat(mockMvc.post().uri(AutoScrapeController.MOVIE_PATH, 5L).with(csrf()))
                .hasStatus(202).bodyJson().extractingPath("$.key").isEqualTo("movie-scrape:5");
    }

    @Test
    void startsTheScrapeOfChosenMovies() {
        when(autoScrapeService.startMovies(List.of(5L, 6L))).thenReturn(job("movies-scrape"));

        assertThat(mockMvc.post().uri(AutoScrapeController.MOVIES_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": [5, 6]}"))
                .hasStatus(202).bodyJson().extractingPath("$.key").isEqualTo("movies-scrape");
    }

    @Test
    void startsTheScrapeOfASingleTvShow() {
        when(autoScrapeService.startTvShow(7L)).thenReturn(job("tvshow-scrape:7"));

        assertThat(mockMvc.post().uri(AutoScrapeController.TV_SHOW_PATH, 7L).with(csrf()))
                .hasStatus(202).bodyJson().extractingPath("$.key").isEqualTo("tvshow-scrape:7");
    }

    @Test
    void startsTheScrapeOfASeason() {
        when(autoScrapeService.startTvSeason(7L, 2, true)).thenReturn(job("tvshow-scrape:7"));
        when(autoScrapeService.startTvSeason(7L, 0, false)).thenReturn(job("tvshow-scrape:7"));

        assertThat(mockMvc.post().uri(AutoScrapeController.TV_SEASON_PATH + "?overwrite=true", 7L, 2).with(csrf()))
                .hasStatus(202).bodyJson().extractingPath("$.key").isEqualTo("tvshow-scrape:7");
        assertThat(mockMvc.post().uri(AutoScrapeController.TV_SEASON_PATH, 7L, 0).with(csrf())).hasStatus(202);
    }

    @Test
    void startsTheScrapeOfChosenTvShows() {
        when(autoScrapeService.startTvShows(List.of(7L))).thenReturn(job("tvshows-scrape"));

        assertThat(mockMvc.post().uri(AutoScrapeController.TV_SHOWS_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"showIds\": [7]}"))
                .hasStatus(202).bodyJson().extractingPath("$.key").isEqualTo("tvshows-scrape");
    }

    @Test
    void rejectsAnEmptySelection() {
        assertThat(mockMvc.post().uri(AutoScrapeController.MOVIES_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": []}")).hasStatus(400);
    }

    private static JobSnapshot job(String key) {
        return new JobSnapshot(JOB_ID, key, "Scrapen", JobStatus.RUNNING, 0, 0, null, null,
                Instant.parse("2026-10-25T12:00:00Z"), null);
    }

    @Test
    void requiresCsrfToken() {
        assertThat(mockMvc.post().uri(AutoScrapeController.AUTO_SCRAPE_PATH, 3L)).hasStatus(403);
    }
}
