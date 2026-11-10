package de.tiktak.kmm.tvshow;

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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.removal.RemovalRequest;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = TvShowRemovalController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class TvShowRemovalControllerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final RemovalPlan PLAN = new RemovalPlan(List.of(LocalizedText.literal("ALF S01E01")),
            List.of("/serien/ALF/S01E01.avi"), List.of(), List.of(), true);

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private TvShowRemovalService removalService;

    @Test
    void previewsTheRemovalOfAnEpisodeASeasonAndAShow() {
        when(removalService.planEpisode(5)).thenReturn(PLAN);
        when(removalService.planSeason(7, 1)).thenReturn(PLAN);
        when(removalService.planShow(7)).thenReturn(PLAN);

        assertThat(mockMvc.get().uri(TvShowRemovalController.EPISODE_REMOVAL_PATH, 5L)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"entries": [{"key": "literal", "args": {"text": "ALF S01E01"}}],
                         "files": ["/serien/ALF/S01E01.avi"], "directories": [], "notes": [],
                         "canDeleteFiles": true}""");
        assertThat(mockMvc.get().uri(TvShowRemovalController.SEASON_REMOVAL_PATH, 7L, 1)).hasStatusOk();
        assertThat(mockMvc.get().uri(TvShowRemovalController.SHOW_REMOVAL_PATH, 7L)).hasStatusOk();
    }

    @Test
    void startsTheRemovalJobs() {
        when(removalService.removeEpisode(5, new RemovalRequest(true))).thenReturn(job());
        when(removalService.removeSeason(7, 1, new RemovalRequest(false))).thenReturn(job());
        when(removalService.removeShow(7, new RemovalRequest(true))).thenReturn(job());

        assertThat(mockMvc.post().uri(TvShowRemovalController.EPISODE_REMOVAL_PATH, 5L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"deleteFiles\": true}"))
                .hasStatus(202).hasHeader("Location", "/api/v1/jobs/" + JOB_ID);
        assertThat(mockMvc.post().uri(TvShowRemovalController.SEASON_REMOVAL_PATH, 7L, 1).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"deleteFiles\": false}"))
                .hasStatus(202);
        assertThat(mockMvc.post().uri(TvShowRemovalController.SHOW_REMOVAL_PATH, 7L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"deleteFiles\": true}"))
                .hasStatus(202);
    }

    @Test
    void answersBlockedFilesWithAConflict() {
        when(removalService.removeShow(7, new RemovalRequest(true))).thenThrow(RemovalProblemException.filesBlocked());

        assertThat(mockMvc.post().uri(TvShowRemovalController.SHOW_REMOVAL_PATH, 7L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"deleteFiles\": true}"))
                .hasStatus(409);
    }

    @Test
    void requiresTheCsrfToken() {
        assertThat(mockMvc.post().uri(TvShowRemovalController.SHOW_REMOVAL_PATH, 7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"deleteFiles\": true}"))
                .hasStatus(403);
    }

    private static JobSnapshot job() {
        return new JobSnapshot(JOB_ID, "removal:episode:5", LocalizedText.literal("ALF S01E01 entfernen"),
                JobStatus.RUNNING, 0, 0, null, null, Instant.parse("2026-11-10T12:00:00Z"), null);
    }
}
