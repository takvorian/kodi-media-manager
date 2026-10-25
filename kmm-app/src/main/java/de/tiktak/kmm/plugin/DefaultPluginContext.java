package de.tiktak.kmm.plugin;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * {@link PluginContext} eines bestimmten Plugins.
 */
record DefaultPluginContext(Map<String, String> settings, HttpClient httpClient, String userAgent)
        implements PluginContext {

    DefaultPluginContext {
        settings = Map.copyOf(settings);
    }

    @Override
    public Optional<String> setting(String name) {
        return Optional.ofNullable(settings.get(name));
    }
}
