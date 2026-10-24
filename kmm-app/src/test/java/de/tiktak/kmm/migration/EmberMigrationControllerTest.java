package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.EmberProfileInspector;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = EmberMigrationController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class EmberMigrationControllerTest {

    private static final String INSPECT_PATH = EmberMigrationController.BASE_PATH + "/inspect";
    private static final Path PROFILE_DIRECTORY = Path.of("ember", "Profiles", "Default");
    private static final EmberProfile PROFILE = new EmberProfile(
            PROFILE_DIRECTORY, PROFILE_DIRECTORY.resolve("MyVideos48.emm"), 48, Optional.empty());
    private static final EmberLibraryStatistics LIBRARY = new EmberLibraryStatistics(3, 1, 1, 2, 1, 1);

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private EmberProfileInspector profileInspector;

    @Test
    void returnsInspectionWithSettings() {
        var settings = new EmberSettings(1, "de-DE", List.of(".mkv"), List.of(".srt"),
                Optional.of(new EmberProxySettings("http://proxy.local", 3128, "proxyuser")));
        when(profileInspector.inspect(Path.of("ember")))
                .thenReturn(new EmberProfileInspection(PROFILE, LIBRARY, Optional.of(settings)));

        assertThat(mockMvc.get().uri(INSPECT_PATH).param("path", "ember"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "schemaVersion": 48,
                          "library": {"movies": 3, "movieSets": 1, "tvShows": 1, "episodes": 2,
                                      "movieSources": 1, "tvShowSources": 1},
                          "settings": {
                            "language": "de-DE",
                            "videoExtensions": [".mkv"],
                            "subtitleExtensions": [".srt"],
                            "proxy": {"uri": "http://proxy.local", "port": 3128, "username": "proxyuser"}
                          }
                        }
                        """);
    }

    @Test
    void returnsInspectionWithoutSettings() {
        when(profileInspector.inspect(any())).thenReturn(new EmberProfileInspection(PROFILE, LIBRARY, Optional.empty()));

        assertThat(mockMvc.get().uri(INSPECT_PATH).param("path", "ember"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> assertThat(json.getJson()).contains("\"settings\":null"))
                .extractingPath("$.profileDirectory").isEqualTo(PROFILE_DIRECTORY.toString());
    }

    @Test
    void answersInvalidProfileWithUnprocessableContent() {
        when(profileInspector.inspect(any())).thenThrow(new InvalidProfileException("Keine Ember-Datenbank gefunden."));

        assertThat(mockMvc.get().uri(INSPECT_PATH).param("path", "leer"))
                .hasStatus(422)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("Keine Ember-Datenbank gefunden.");
    }

    @Test
    void answersInvalidPathWithBadRequest() {
        assertThat(mockMvc.get().uri(INSPECT_PATH).param("path", "invalid\u0000path"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.title").isEqualTo(EmberMigrationController.INVALID_PROFILE_TITLE);
    }

    @Test
    void requiresPathParameter() {
        assertThat(mockMvc.get().uri(INSPECT_PATH)).hasStatus(400);
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() {
        assertThat(mockMvc.get().uri(INSPECT_PATH).param("path", "ember")).hasStatus(401);
    }

    /** Macht den geschützten Konstruktor für Testfälle zugänglich. */
    private static final class InvalidProfileException extends EmberProfileException {

        InvalidProfileException(String message) {
            super(message);
        }
    }
}
