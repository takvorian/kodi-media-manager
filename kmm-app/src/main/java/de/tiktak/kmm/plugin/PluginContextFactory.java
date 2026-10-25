package de.tiktak.kmm.plugin;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import de.tiktak.kmm.plugin.api.PluginContext;
import lombok.RequiredArgsConstructor;

/**
 * Erzeugt den {@link PluginContext} für ein Plugin aus der Konfiguration der Anwendung. Gemeinsame
 * Einstellungen (z. B. die Sprache) gelten für alle Plugins, eigene Einstellungen eines Plugins haben
 * Vorrang. Da PF4J Extensions bei jeder Abfrage neu erzeugt, wirken Änderungen sofort.
 */
@RequiredArgsConstructor
class PluginContextFactory {

    static final String USER_AGENT_TEMPLATE = "KodiMediaManager/%s (+https://github.com/takvorian/kodi-media-manager)";
    static final String DEVELOPMENT_VERSION = "dev";

    private final PluginProperties properties;
    private final HttpClient httpClient;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Supplier<Map<String, String>> sharedSettings;

    PluginContext contextFor(String pluginId) {
        Map<String, String> settings = new HashMap<>(sharedSettings.get());
        settings.putAll(properties.settingsOf(pluginId));
        return new DefaultPluginContext(settings, httpClient, userAgent());
    }

    private String userAgent() {
        BuildProperties build = buildProperties.getIfAvailable();
        return USER_AGENT_TEMPLATE.formatted(build == null ? DEVELOPMENT_VERSION : build.getVersion());
    }
}
