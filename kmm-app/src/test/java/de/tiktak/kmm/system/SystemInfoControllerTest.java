package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = SystemInfoController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
class SystemInfoControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private SystemInfoService systemInfoService;

    @MockitoBean
    private ReleaseInfoService releaseInfoService;

    @Test
    void returnsSystemInfoWithoutAuthentication() {
        when(systemInfoService.currentSystemInfo())
                .thenReturn(new SystemInfo("Kodi Media Manager", "0.1.0", "25.0.4", List.of("standalone"), "H2"));

        assertThat(mockMvc.get().uri(SystemInfoController.INFO_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "applicationName": "Kodi Media Manager",
                          "version": "0.1.0",
                          "javaVersion": "25.0.4",
                          "activeProfiles": ["standalone"],
                          "database": "H2"
                        }
                        """);
    }

    @Test
    void returnsTheChangelogOnlyAfterAuthentication() {
        when(releaseInfoService.changelog())
                .thenReturn(List.of(new ChangelogVersion("v0.1", "2026-10-30", List.of("Erste (abc1234)"))));

        assertThat(mockMvc.get().uri(SystemInfoController.CHANGELOG_PATH)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mockMvc.get().uri(SystemInfoController.CHANGELOG_PATH).with(user("admin")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"version": "v0.1", "date": "2026-10-30", "changes": ["Erste (abc1234)"]}]
                        """);
    }
}
