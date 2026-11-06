package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = KodiPlaybackController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class KodiPlaybackControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T20:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private KodiPlaybackService playbackService;

    @Test
    void startsTheImportOfASourceAsJob() {
        UUID jobId = UUID.randomUUID();
        when(playbackService.startSourceImport(3)).thenReturn(new JobSnapshot(jobId, "kodi-playback:3",
                LocalizedText.of("kodi.playback.jobTitle").with("source", "Filme"), JobStatus.RUNNING, 0, 0, null, null, NOW, null));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/sources/3/playback").with(csrf()))
                .hasStatus(HttpStatus.ACCEPTED).hasHeader("Location", "/api/v1/jobs/" + jobId);
    }

    @Test
    void takesOverMoviesAndEpisodes() {
        when(playbackService.importMovie(5)).thenReturn(new KodiPlaybackResult("Wohnzimmer",
                KodiPlaybackOutcome.UPDATED, LocalizedText.of("kodi.playback.updated").with("label", "Heat"), 2, NOW, 8));
        when(playbackService.importEpisode(9)).thenReturn(new KodiPlaybackResult("Wohnzimmer",
                KodiPlaybackOutcome.NOT_IN_KODI, LocalizedText.of("kodi.playback.notInKodi").with("label", "Lost S01E01"),
                null, null, null));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/5/playback").with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"host": "Wohnzimmer", "outcome": "UPDATED", "playCount": 2,
                         "lastPlayedAt": "2026-10-26T20:00:00Z", "userRating": 8}
                        """);
        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/episodes/9/playback").with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$.outcome").isEqualTo("NOT_IN_KODI");
    }
}
