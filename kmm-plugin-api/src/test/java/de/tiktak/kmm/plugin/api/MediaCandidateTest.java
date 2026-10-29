package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

class MediaCandidateTest {

    private static final URI AUDIO = URI.create("http://televisiontunes.com/uploads/audio/Alf.mp3");
    private static final URI PAGE = URI.create("http://televisiontunes.com/Alf_2.html");

    @Test
    void createsAThemeWithSourceAndPage() {
        ThemeCandidate theme = new ThemeCandidate("Main Theme", "ALF", AUDIO, AUDIO, PAGE, 30);

        assertThat(theme.source()).isEqualTo(AUDIO);
        assertThat(theme.durationSeconds()).isEqualTo(30);
    }

    @Test
    void requiresSourceAndPageOfATheme() {
        assertThatThrownBy(() -> new ThemeCandidate("x", "ALF", null, null, PAGE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ThemeCandidate("x", "ALF", AUDIO, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresTheKodiLinkOfATrailer() {
        assertThatThrownBy(() -> new TrailerCandidate("Trailer", null, PAGE, null, "de", "Trailer", true, 1080, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new TrailerCandidate("Trailer", "plugin://x", PAGE, null, null, "Trailer", false, null, null)
                .kodiUrl()).isEqualTo("plugin://x");
    }
}
