package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = KodiSyncController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class KodiSyncControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T20:00:00Z");
    private static final String JOB_1 = "00000000-0000-0000-0000-000000000001";
    private static final String JOB_2 = "00000000-0000-0000-0000-000000000002";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private KodiSyncService syncService;

    @MockitoBean
    private KodiBatchRefreshService batchRefreshService;

    @Test
    void refreshesAMovie() {
        when(syncService.refreshMovie(5)).thenReturn(List.of(
                new KodiSyncResult(1, "Wohnzimmer", KodiSyncOutcome.REFRESHED, refreshed("Heat"), NOW)));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/5/refresh").with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"host": "Wohnzimmer", "outcome": "REFRESHED",
                          "message": {"key": "kodi.sync.refreshed", "args": {"title": "Heat"}}}]
                        """);
    }

    @Test
    void refreshesTheChosenMoviesAsAJob() {
        when(batchRefreshService.startMovies(List.of(5L, 6L))).thenReturn(job(JOB_1));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/refresh").with(csrf())
                .contentType("application/json").content("{\"movieIds\": [5, 6]}"))
                .hasStatus(202).bodyJson().extractingPath("$.id").isEqualTo(JOB_1);
    }

    @Test
    void refreshesTheChosenTvShowsAsAJob() {
        when(batchRefreshService.startTvShows(List.of(7L))).thenReturn(job(JOB_2));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/tvshows/refresh").with(csrf())
                .contentType("application/json").content("{\"showIds\": [7]}"))
                .hasStatus(202).bodyJson().extractingPath("$.id").isEqualTo(JOB_2);
    }

    @Test
    void rejectsAnEmptySelection() {
        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/refresh").with(csrf())
                .contentType("application/json").content("{\"movieIds\": []}")).hasStatus(400);
    }

    private static LocalizedText refreshed(String title) {
        return LocalizedText.of("kodi.sync.refreshed").with("title", title);
    }

    private static JobSnapshot job(String id) {
        return new JobSnapshot(UUID.fromString(id), "movies-batch", LocalizedText.literal("Kodi"), JobStatus.RUNNING, 0, 0, null, null, NOW, null);
    }

    @Test
    void refreshesATvShowOrOneOfItsSeasonsWithTheEpisodesAsAJob() {
        when(batchRefreshService.startTvShow(7, OptionalInt.empty())).thenReturn(job(JOB_1));
        when(batchRefreshService.startTvShow(7, OptionalInt.of(0))).thenReturn(job(JOB_2));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/tvshows/7/refresh").with(csrf()))
                .hasStatus(202).hasHeader("Location", "/api/v1/jobs/" + JOB_1)
                .bodyJson().extractingPath("$.id").isEqualTo(JOB_1);
        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/tvshows/7/seasons/0/refresh").with(csrf()))
                .hasStatus(202).bodyJson().extractingPath("$.id").isEqualTo(JOB_2);
    }

    @Test
    void rejectsANegativeSeason() {
        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/tvshows/7/seasons/-1/refresh").with(csrf()))
                .hasStatus(400);
    }

    @Test
    void refreshesAnEpisode() {
        when(syncService.refreshEpisode(12)).thenReturn(List.of(
                new KodiSyncResult(1, "Wohnzimmer", KodiSyncOutcome.REFRESHED, refreshed("Lost S01E02"), NOW)));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/episodes/12/refresh").with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$[0].message.args.title").isEqualTo("Lost S01E02");
    }

    @Test
    void scansASource() {
        when(syncService.scanSource(3)).thenReturn(List.of());

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/sources/3/scan").with(csrf()))
                .hasStatusOk().bodyJson().isEqualTo("[]");
    }
}
