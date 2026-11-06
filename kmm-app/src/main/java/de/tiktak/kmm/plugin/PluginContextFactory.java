package de.tiktak.kmm.plugin;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import de.tiktak.kmm.plugin.api.PluginContext;
import lombok.RequiredArgsConstructor;

/**
 * Creates the {@link PluginContext} for a plugin from the application configuration. Shared
 * settings (e.g. the language) apply to all plugins, a plugin's own settings take
 * precedence. Since PF4J creates extensions anew on every lookup, changes take effect immediately.
 */
@RequiredArgsConstructor
class PluginContextFactory {

    static final String USER_AGENT_TEMPLATE = "KodiMediaManager/%s (+https://github.com/takvorian/kodi-media-manager)";
    static final String DEVELOPMENT_VERSION = "dev";

    private final PluginProperties properties;
    private final HttpClient httpClient;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Supplier<Map<String, String>> sharedSettings;
    private final BiFunction<String, Map<String, String>, Map<String, String>> storedKeys;

    /** Shared settings, then the plugin's settings from the configuration, finally stored API keys. */
    PluginContext contextFor(String pluginId) {
        Map<String, String> settings = new HashMap<>(sharedSettings.get());
        settings.putAll(properties.settingsOf(pluginId));
        return new DefaultPluginContext(storedKeys.apply(pluginId, settings), httpClient, userAgent());
    }

    private String userAgent() {
        BuildProperties build = buildProperties.getIfAvailable();
        return USER_AGENT_TEMPLATE.formatted(build == null ? DEVELOPMENT_VERSION : build.getVersion());
    }
}
