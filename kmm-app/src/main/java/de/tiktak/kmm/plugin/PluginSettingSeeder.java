package de.tiktak.kmm.plugin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.settings.PluginSettingService;
import lombok.RequiredArgsConstructor;

/**
 * On startup, takes over the keys of the started plugins from the configuration ({@code kmm.plugins.settings.*},
 * filled from environment variables such as {@code KMM_TMDB_API_KEY}) into the settings store as long as there is
 * none there yet. After that the stored key applies, even if the environment variable stays set. Other settings are
 * not copied; without a stored value the configuration keeps applying to them.
 */
@Component
@RequiredArgsConstructor
class PluginSettingSeeder implements ApplicationRunner {

    private final PluginProperties properties;
    private final ScraperOverviewService overviewService;
    private final PluginSettingService pluginSettingService;

    @Override
    public void run(ApplicationArguments arguments) {
        overviewService.settingDefinitions().forEach((pluginId, definitions) -> definitions.stream()
                .filter(definition -> definition.secret())
                .forEach(definition -> pluginSettingService.seedIfMissing(pluginId, definition.name(),
                        properties.settingsOf(pluginId).get(definition.name()))));
    }
}
