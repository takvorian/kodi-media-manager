package de.tiktak.kmm.plugin;

import java.net.http.HttpClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import de.tiktak.kmm.plugin.api.PluginContext;
import lombok.RequiredArgsConstructor;

/**
 * Erzeugt den {@link PluginContext} für ein Plugin aus der Konfiguration der Anwendung.
 */
@RequiredArgsConstructor
class PluginContextFactory {

    static final String USER_AGENT_TEMPLATE = "KodiMediaManager/%s (+https://github.com/takvorian/kodi-media-manager)";
    static final String DEVELOPMENT_VERSION = "dev";

    private final PluginProperties properties;
    private final HttpClient httpClient;
    private final ObjectProvider<BuildProperties> buildProperties;

    PluginContext contextFor(String pluginId) {
        return new DefaultPluginContext(properties.settingsOf(pluginId), httpClient, userAgent());
    }

    private String userAgent() {
        BuildProperties build = buildProperties.getIfAvailable();
        return USER_AGENT_TEMPLATE.formatted(build == null ? DEVELOPMENT_VERSION : build.getVersion());
    }
}
