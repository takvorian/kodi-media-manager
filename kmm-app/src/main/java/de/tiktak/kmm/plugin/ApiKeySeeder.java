package de.tiktak.kmm.plugin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.settings.ApiKey;
import de.tiktak.kmm.settings.ApiKeyService;
import lombok.RequiredArgsConstructor;

/**
 * Übernimmt beim Start API-Keys aus der Konfiguration ({@code kmm.plugins.settings.*}, gefüllt aus
 * Umgebungsvariablen wie {@code KMM_TMDB_API_KEY}) in den Einstellungsspeicher, solange dort noch
 * keiner steht. Danach gilt der gespeicherte Key, auch wenn die Umgebungsvariable gesetzt bleibt.
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
