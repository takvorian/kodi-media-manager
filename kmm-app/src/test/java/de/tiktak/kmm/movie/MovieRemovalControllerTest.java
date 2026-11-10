package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = MovieRemovalController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MovieRemovalControllerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final RemovalPlan PLAN = new RemovalPlan(List.of(LocalizedText.literal("Heat (1995)")),
            List.of("/filme/Heat/Heat.mkv"), List.of("/filme/Heat"), List.of(), true);

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovieRemovalService removalService;

    @Test
    void previewsTheRemovalOfAMovieAndOfASelection() {
        when(removalService.plan(List.of(7L))).thenReturn(PLAN);
        when(removalService.plan(List.of(7L, 8L))).thenReturn(PLAN);

        assertThat(mockMvc.get().uri(MovieRemovalController.MOVIE_REMOVAL_PATH, 7L)).hasStatusOk()
                .bodyJson().extractingPath("$.directories[0]").isEqualTo("/filme/Heat");
        assertThat(mockMvc.post().uri(MovieRemovalController.SELECTION_PLAN_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": [7, 8]}"))
                .hasStatusOk().bodyJson().extractingPath("$.files[0]").isEqualTo("/filme/Heat/Heat.mkv");
    }

    @Test
    void startsTheRemovalJobs() {
        when(removalService.remove(List.of(7L), true)).thenReturn(job());
        when(removalService.remove(List.of(7L, 8L), false)).thenReturn(job());

        assertThat(mockMvc.post().uri(MovieRemovalController.MOVIE_REMOVAL_PATH, 7L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"deleteFiles\": true}"))
                .hasStatus(202).hasHeader("Location", "/api/v1/jobs/" + JOB_ID);
        assertThat(mockMvc.post().uri(MovieRemovalController.SELECTION_REMOVAL_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": [7, 8], \"deleteFiles\": false}"))
                .hasStatus(202);
    }

    @Test
    void rejectsAnEmptySelection() {
        assertThat(mockMvc.post().uri(MovieRemovalController.SELECTION_REMOVAL_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": [], \"deleteFiles\": true}"))
                .hasStatus4xxClientError();
        verify(removalService, never()).remove(anyList(), anyBoolean());
    }

    private static JobSnapshot job() {
        return new JobSnapshot(JOB_ID, "removal:movie:7", LocalizedText.literal("Heat entfernen"),
                JobStatus.RUNNING, 0, 0, null, null, Instant.parse("2026-11-10T12:00:00Z"), null);
    }
}
