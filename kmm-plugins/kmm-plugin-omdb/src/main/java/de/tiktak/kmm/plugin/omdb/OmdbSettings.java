package de.tiktak.kmm.plugin.omdb;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.omdb.*}).
 *
 * @param apiKey  OMDb API key; empty if not set up
 * @param baseUrl base URL of the API
 */
record OmdbSettings(String apiKey, URI baseUrl) {

    static final String API_KEY = "api-key";
    static final String BASE_URL = "base-url";
    static final String DEFAULT_BASE_URL = "https://www.omdbapi.com";

    static OmdbSettings from(PluginContext context) {
        String url = context.setting(BASE_URL).map(String::strip).filter(value -> !value.isEmpty())
                .orElse(DEFAULT_BASE_URL);
        return new OmdbSettings(context.setting(API_KEY).map(String::strip).orElse(""),
                URI.create(url.replaceAll("/+$", "")));
    }

    boolean hasApiKey() {
        return !apiKey.isEmpty();
    }
}
