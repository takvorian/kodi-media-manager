package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = PluginController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class PluginControllerTest {

    private static final PluginInfo STARTED = new PluginInfo("tmdb", "1.0.0", "TMDB", "tiktak.de", "STARTED");
    private static final PluginInfo DISABLED = new PluginInfo("tmdb", "1.0.0", "TMDB", "tiktak.de", "DISABLED");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private PluginService pluginService;

    @Test
    void listsInstalledPlugins() {
        when(pluginService.installedPlugins()).thenReturn(List.of(STARTED));

        assertThat(mockMvc.get().uri(PluginController.BASE_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "tmdb", "version": "1.0.0", "description": "TMDB", "provider": "tiktak.de", "state": "STARTED"}]
                        """);
    }

    @Test
    void enablesPlugin() {
        when(pluginService.enable("tmdb")).thenReturn(STARTED);

        assertThat(mockMvc.post().uri(PluginController.BASE_PATH + "/tmdb/enable").with(csrf()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.state").isEqualTo("STARTED");
    }

    @Test
    void disablesPlugin() {
        when(pluginService.disable("tmdb")).thenReturn(DISABLED);

        assertThat(mockMvc.post().uri(PluginController.BASE_PATH + "/tmdb/disable").with(csrf()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.state").isEqualTo("DISABLED");
    }

    @Test
    void answersUnknownPluginWithProblemDetail() {
        when(pluginService.enable("unknown")).thenThrow(new PluginNotFoundException("unknown"));

        assertThat(mockMvc.post().uri(PluginController.BASE_PATH + "/unknown/enable").with(csrf()))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.title").isEqualTo("Plugin nicht gefunden");
    }
}
