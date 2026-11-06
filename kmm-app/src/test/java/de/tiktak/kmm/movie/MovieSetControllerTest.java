package de.tiktak.kmm.movie;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Set scraping in the movie set manager; the management is tested by {@link MovieSetIntegrationTest}.
 */
@WebMvcTest(controllers = MovieSetController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MovieSetControllerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovieSetService setService;

    @MockitoBean
    private MovieSetJobService jobService;

    @MockitoBean
    private MovieSetScrapeService scrapeService;

    @Test
    void searchesCollections() {
        when(scrapeService.search("Matrix")).thenReturn(List.of(
                new ScrapeCandidate("2344", "Matrix Filmreihe", null, null, "Neo.", null)));

        assertThat(mockMvc.get().uri(MovieSetController.BASE_PATH + MovieSetController.SEARCH_PATH)
                .param("title", "Matrix"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"providerId": "2344", "title": "Matrix Filmreihe", "overview": "Neo."}]
                        """);
    }

    @Test
    void rejectsASearchWithoutTitle() {
        assertThat(mockMvc.get().uri(MovieSetController.BASE_PATH + MovieSetController.SEARCH_PATH)
                .param("title", " ")).hasStatus4xxClientError();
    }

    @Test
    void proposesTheDataOfAMatch() {
        when(scrapeService.proposal(7L, "2344"))
                .thenReturn(new MovieSetData("Matrix Filmreihe", "Neo.", "2344", SetSortMethod.YEAR));

        assertThat(mockMvc.get().uri(MovieSetController.BASE_PATH + MovieSetController.PROPOSAL_PATH, 7L)
                .param("providerId", "2344"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name": "Matrix Filmreihe", "overview": "Neo.", "tmdbCollectionId": "2344",
                         "sortMethod": "YEAR"}
                        """);
    }

    @Test
    void startsTheCompletionOfAllSetsAsJob() {
        when(jobService.completeAll()).thenReturn(new JobSnapshot(JOB_ID, "moviesets", LocalizedText.literal("Alle Sets ergänzen"),
                JobStatus.RUNNING, 0, 0, null, null, Instant.parse("2026-10-28T12:00:00Z"), null));

        assertThat(mockMvc.post().uri(MovieSetController.BASE_PATH + MovieSetController.COMPLETE_PATH).with(csrf()))
                .hasStatus(202)
                .hasHeader("Location", "/api/v1/jobs/" + JOB_ID);
    }
}
