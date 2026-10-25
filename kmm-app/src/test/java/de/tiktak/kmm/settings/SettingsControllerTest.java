package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = SettingsController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class SettingsControllerTest {

    private static final SettingsView VIEW = new SettingsView("de-DE", "de", List.of("mkv"), List.of(),
            new SettingsView.Proxy("proxy.local", 3128, "user", true), Set.of(SettingsField.ARTWORK_LANGUAGE));

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void returnsEffectiveSettingsWithoutPassword() {
        when(settingsService.view()).thenReturn(VIEW);

        assertThat(mockMvc.get().uri(SettingsController.BASE_PATH)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"metadataLanguage": "de-DE", "videoExtensions": ["mkv"],
                 "proxy": {"host": "proxy.local", "port": 3128, "username": "user", "passwordSet": true},
                 "overridden": ["ARTWORK_LANGUAGE"]}
                """);
    }

    @Test
    void savesValidSettings() {
        when(settingsService.update(any())).thenReturn(VIEW);

        assertThat(mockMvc.put().uri(SettingsController.BASE_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metadataLanguage": "en-US", "artworkLanguage": "en", "videoExtensions": [".MKV"],
                         "excludedPaths": [], "proxy": null}
                        """))
                .hasStatusOk();
        verify(settingsService).update(new KmmSettings("en-US", "en", List.of("mkv"), List.of(), null));
    }

    @Test
    void rejectsInvalidSettings() {
        assertThat(mockMvc.put().uri(SettingsController.BASE_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metadataLanguage": "deutsch", "artworkLanguage": "de", "videoExtensions": ["mkv"],
                         "excludedPaths": []}
                        """))
                .hasStatus(400);
        verifyNoInteractions(settingsService);
    }
}
