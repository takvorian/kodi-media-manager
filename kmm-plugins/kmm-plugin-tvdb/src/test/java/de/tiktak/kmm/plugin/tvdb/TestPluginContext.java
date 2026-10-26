package de.tiktak.kmm.plugin.tvdb;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * {@link PluginContext} für Tests mit frei setzbaren Einstellungen.
 */
final class TestPluginContext implements PluginContext {

    static final String USER_AGENT = "KMM-Test/1.0";

    private final Map<String, String> settings = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    TestPluginContext with(String name, String value) {
        settings.put(name, value);
        return this;
    }

    @Override
    public Optional<String> setting(String name) {
        return Optional.ofNullable(settings.get(name));
    }

    @Override
    public HttpClient httpClient() {
        return httpClient;
    }

    @Override
    public String userAgent() {
        return USER_AGENT;
    }
}
