package de.tiktak.kmm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.plugin.PluginController;
import de.tiktak.kmm.plugin.PluginInfo;
import de.tiktak.kmm.plugin.PluginService;
import de.tiktak.kmm.system.ReleaseInfoService;
import de.tiktak.kmm.system.SystemInfo;
import de.tiktak.kmm.system.SystemInfoController;
import de.tiktak.kmm.system.SystemInfoService;

class SecurityConfigurationTest {

    private static final String USER = "tester";
    private static final String PASSWORD = "secret";
    private static final String PLUGIN_ID = "testdata";
    private static final String ENABLE_PATH = PluginController.BASE_PATH + "/" + PLUGIN_ID + "/enable";
    private static final PluginInfo PLUGIN = new PluginInfo(PLUGIN_ID, "1.0.0", "Test", "tiktak.de", "STARTED");

    @Nested
    @WebMvcTest(controllers = {SystemInfoController.class, PluginController.class}, properties = {
            "kmm.security.enabled=true",
            "spring.security.user.name=" + USER,
            "spring.security.user.password=" + PASSWORD})
    @Import(WebSecurityTestConfiguration.class)
    class WhenEnabled {

        @Autowired
        private MockMvcTester mockMvc;

        @MockitoBean
        private SystemInfoService systemInfoService;

        @MockitoBean
        private ReleaseInfoService releaseInfoService;

        @MockitoBean
        private PluginService pluginService;

        @Test
        void allowsSystemInfoWithoutAuthentication() {
            when(systemInfoService.currentSystemInfo()).thenReturn(new SystemInfo("KMM", "1", "25", List.of(), "H2"));

            assertThat(mockMvc.get().uri(SystemInfoController.INFO_PATH)).hasStatusOk();
        }

        @Test
        void rejectsApiRequestWithoutAuthentication() {
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH)).hasStatus(401);
        }

        @Test
        void rejectsWrongPassword() {
            assertThat(mockMvc.get().uri(PluginController.BASE_PATH).with(httpBasic(USER, "wrong"))).hasStatus(401);
        }

        @Test
        void allowsApiRequestWithValidCredentials() {
            when(pluginService.installedPlugins()).thenReturn(List.of(PLUGIN));

            assertThat(mockMvc.get().uri(PluginController.BASE_PATH).with(httpBasic(USER, PASSWORD))).hasStatusOk();
        }

        @Test
        void rejectsStateChangingRequestWithoutCsrfToken() {
            assertThat(mockMvc.post().uri(ENABLE_PATH).with(httpBasic(USER, PASSWORD))).hasStatus(403);
        }

        @Test
        void allowsStateChangingRequestWithCsrfToken() {
            when(pluginService.enable(PLUGIN_ID)).thenReturn(PLUGIN);

            assertThat(mockMvc.post().uri(ENABLE_PATH).with(httpBasic(USER, PASSWORD)).with(csrf())).hasStatusOk();
        }

        @Test
        void allowsClientRoutesWithoutAuthentication() {
            assertThat(mockMvc.get().uri("/movies")).hasStatusOk();
        }
    }

    @Nested
    @WebMvcTest(controllers = PluginController.class, properties = "kmm.security.enabled=false")
    @Import(WebSecurityTestConfiguration.class)
    class WhenDisabled {

        @Autowired
        private MockMvcTester mockMvc;

        @MockitoBean
        private PluginService pluginService;

        @Test
        void allowsApiRequestWithoutAuthentication() {
            when(pluginService.installedPlugins()).thenReturn(List.of(PLUGIN));

            assertThat(mockMvc.get().uri(PluginController.BASE_PATH)).hasStatusOk();
        }

        @Test
        void stillRequiresCsrfTokenForStateChangingRequests() {
            assertThat(mockMvc.post().uri(ENABLE_PATH)).hasStatus(403);
        }
    }
}
