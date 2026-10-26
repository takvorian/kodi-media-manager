package de.tiktak.kmm.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = JobController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class JobControllerTest {

    private static final UUID JOB_ID = UUID.fromString("7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f00");
    private static final JobSnapshot JOB = JobSnapshot.started(JOB_ID, "scan:1", "Scan „Filme“",
            Instant.parse("2026-10-25T12:00:00Z")).withProgress(3, 10, "NFOs werden geprüft");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobEventBroadcaster broadcaster;

    @Test
    void listsJobs() {
        when(jobService.findAll()).thenReturn(List.of(JOB));

        assertThat(mockMvc.get().uri(JobController.BASE_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f00", "key": "scan:1", "status": "RUNNING",
                          "done": 3, "total": 10, "message": "NFOs werden geprüft"}]
                        """);
    }

    @Test
    void returnsSingleJob() {
        when(jobService.find(JOB_ID)).thenReturn(JOB);

        assertThat(mockMvc.get().uri(JobController.BASE_PATH + "/" + JOB_ID))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.title").isEqualTo("Scan „Filme“");
    }

    @Test
    void answersUnknownJobWithNotFound() {
        when(jobService.find(JOB_ID)).thenThrow(new JobNotFoundException(JOB_ID));

        assertThat(mockMvc.get().uri(JobController.BASE_PATH + "/" + JOB_ID)).hasStatus(404);
    }

    @Test
    void cancelsJob() {
        when(jobService.cancel(JOB_ID)).thenReturn(JOB);

        assertThat(mockMvc.post().uri(JobController.BASE_PATH + "/" + JOB_ID + "/cancel").with(csrf()))
                .hasStatusOk();
    }

    @Test
    void streamsJobEvents() {
        when(jobService.findAll()).thenReturn(List.of(JOB));
        when(broadcaster.connect(anyList())).thenReturn(new SseEmitter());

        assertThat(mockMvc.get().uri(JobController.EVENTS_PATH).accept("text/event-stream").asyncExchange())
                .request().hasAsyncStarted(true);
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() {
        assertThat(mockMvc.get().uri(JobController.BASE_PATH)).hasStatus(401);
    }
}
