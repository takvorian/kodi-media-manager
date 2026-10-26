package de.tiktak.kmm.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.job.JobAlreadyRunningException;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.movie.MovieScanService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.TvShowScanService;

@WebMvcTest(controllers = LibraryScanController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class LibraryScanControllerTest {

    private static final UUID JOB_ID = UUID.fromString("7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f00");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MediaSourceService sourceService;

    @MockitoBean
    private MovieScanService movieScanService;

    @MockitoBean
    private TvShowScanService tvShowScanService;

    @Test
    void scansMovieSourcesAsMovies() {
        when(sourceService.require(1)).thenReturn(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));
        when(movieScanService.startScan(1)).thenReturn(job("Scan „Filme“"));

        assertThat(mockMvc.post().uri(LibraryScanController.SCAN_PATH, 1L).with(csrf()))
                .hasStatus(202)
                .hasHeader("Location", "/api/v1/jobs/" + JOB_ID)
                .bodyJson().isLenientlyEqualTo("{\"key\": \"scan:1\", \"status\": \"RUNNING\"}");
        verifyNoInteractions(tvShowScanService);
    }

    @Test
    void scansTvSourcesAsTvShows() {
        when(sourceService.require(1)).thenReturn(new MediaSource("Serien", "/media/serien", MediaType.TV_SHOW));
        when(tvShowScanService.startScan(1)).thenReturn(job("Scan „Serien“"));

        assertThat(mockMvc.post().uri(LibraryScanController.SCAN_PATH, 1L).with(csrf())).hasStatus(202);
        verifyNoInteractions(movieScanService);
    }

    @Test
    void answersASecondScanWithConflict() {
        when(sourceService.require(1)).thenReturn(new MediaSource("Filme", "/media/filme", MediaType.MOVIE));
        when(movieScanService.startScan(1)).thenThrow(new JobAlreadyRunningException(job("Scan „Filme“")));

        assertThat(mockMvc.post().uri(LibraryScanController.SCAN_PATH, 1L).with(csrf()))
                .hasStatus(409).bodyJson().extractingPath("$.detail").isEqualTo("Scan „Filme“ läuft bereits.");
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() {
        assertThat(mockMvc.post().uri(LibraryScanController.SCAN_PATH, 1L).with(csrf())).hasStatus(401);
    }

    private static JobSnapshot job(String title) {
        return new JobSnapshot(JOB_ID, "scan:1", title, JobStatus.RUNNING, 0, 0, null, null,
                Instant.parse("2026-10-26T12:00:00Z"), null);
    }
}
