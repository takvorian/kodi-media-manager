package de.tiktak.kmm.migration.ember.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class EmberSettingsTest {

    @Test
    void copiesExtensionListsDefensively() {
        List<String> videoExtensions = new ArrayList<>(List.of(".mkv"));
        var settings = new EmberSettings(1, "de-DE", videoExtensions, List.of(), Optional.empty(), null, null);

        videoExtensions.add(".avi");

        assertThat(settings.videoExtensions()).containsExactly(".mkv");
    }

    @Test
    void rejectsNullInsteadOfEmptyProxy() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EmberSettings(1, "de-DE", List.of(), List.of(), null, null, null))
                .withMessageContaining("proxy");
    }
}
