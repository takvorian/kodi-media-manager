package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.settings.EpisodeOrdering;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.TvSettings;

class PluginManagerConfigurationTest {

    @Test
    void sharesLanguagesAndEpisodeOrderingWithAllPlugins() {
        KmmSettings settings = KmmSettings.DEFAULTS.withTv(new TvSettings("en-US", "en", EpisodeOrdering.DVD));

        assertThat(PluginManagerConfiguration.sharedSettings(settings)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "language", KmmSettings.DEFAULTS.metadataLanguage(),
                "tv-language", "en-US",
                "episode-ordering", "DVD"));
    }

    @Test
    void givesTheLanguageOfTheProcessPrecedence() {
        KmmSettings settings = KmmSettings.DEFAULTS.withTv(new TvSettings("en-US", "en", EpisodeOrdering.DVD));

        Map<String, String> shared = ScrapeLanguage.with("fr-FR", () -> PluginManagerConfiguration.sharedSettings(settings));

        assertThat(shared).containsEntry("language", "fr-FR").containsEntry("tv-language", "fr-FR")
                .containsEntry("episode-ordering", "DVD");
    }
}
