package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
    void requiresCsrfToken() {
        assertThat(mockMvc.post().uri(AutoScrapeController.AUTO_SCRAPE_PATH, 3L)).hasStatus(403);
    }
}
