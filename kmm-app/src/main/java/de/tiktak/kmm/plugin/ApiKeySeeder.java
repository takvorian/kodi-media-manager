package de.tiktak.kmm.plugin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.settings.ApiKey;
import de.tiktak.kmm.settings.ApiKeyService;
import lombok.RequiredArgsConstructor;

/**
 * On startup, takes over API keys from the configuration ({@code kmm.plugins.settings.*}, filled from
 * environment variables such as {@code KMM_TMDB_API_KEY}) into the settings store as long as there is
 * none there yet. After that the stored key applies, even if the environment variable stays set.
 */
@Component
@RequiredArgsConstructor
class ApiKeySeeder implements ApplicationRunner {

    private final PluginProperties properties;
    private final ApiKeyService apiKeyService;

    @Override
    public void run(ApplicationArguments arguments) {
        for (ApiKey key : ApiKey.values()) {
            apiKeyService.seedIfMissing(key, properties.settingsOf(key.pluginId()).get(key.setting()));
        }
    }
}
