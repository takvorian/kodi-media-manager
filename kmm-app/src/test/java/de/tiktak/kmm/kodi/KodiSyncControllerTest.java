package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = KodiSyncController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class KodiSyncControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T20:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private KodiSyncService syncService;

    @Test
    void refreshesAMovie() {
        when(syncService.refreshMovie(5)).thenReturn(List.of(
                new KodiSyncResult(1, "Wohnzimmer", KodiSyncOutcome.REFRESHED, "„Heat“ aktualisiert.", NOW)));

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/movies/5/refresh").with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"host": "Wohnzimmer", "outcome": "REFRESHED", "message": "„Heat“ aktualisiert."}]
                        """);
    }

    @Test
    void refreshesTvShowsAndEpisodes() {
        when(syncService.refreshTvShow(7)).thenReturn(List.of(
                new KodiSyncResult(1, "Wohnzimmer", KodiSyncOutcome.REFRESHED, "„Lost“ aktualisiert.", NOW)));
        when(syncService.refreshEpisode(12)).thenReturn(List.of());

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/tvshows/7/refresh").with(csrf()))
                .hasStatusOk().bodyJson().extractingPath("$[0].message").isEqualTo("„Lost“ aktualisiert.");
        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/episodes/12/refresh").with(csrf()))
                .hasStatusOk().bodyJson().isEqualTo("[]");
    }

    @Test
    void scansASource() {
        when(syncService.scanSource(3)).thenReturn(List.of());

        assertThat(mockMvc.post().uri(KodiSyncController.BASE_PATH + "/sources/3/scan").with(csrf()))
                .hasStatusOk().bodyJson().isEqualTo("[]");
    }
}
