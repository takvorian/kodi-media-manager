package de.tiktak.kmm.plugin.omdb;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.omdb.*}).
 *
 * @param apiKey  OMDb-API-Key; leer, wenn nicht eingerichtet
 * @param baseUrl Basis-URL der API
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
