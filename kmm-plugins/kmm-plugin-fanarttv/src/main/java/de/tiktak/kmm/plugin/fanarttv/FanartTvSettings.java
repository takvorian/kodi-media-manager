package de.tiktak.kmm.plugin.fanarttv;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.fanarttv.*}).
 *
 * @param apiKey    Projekt-API-Key von fanart.tv; leer, wenn nicht konfiguriert
 * @param clientKey optionaler persönlicher Key; neue Bilder erscheinen damit früher
 * @param baseUrl   Basis-URL der API
 */
record FanartTvSettings(String apiKey, String clientKey, URI baseUrl) {

    static final String API_KEY = "api-key";
    static final String CLIENT_KEY = "client-key";
    static final String BASE_URL = "base-url";
    static final String DEFAULT_BASE_URL = "https://webservice.fanart.tv/v3";

    static FanartTvSettings from(PluginContext context) {
        return new FanartTvSettings(
                text(context, API_KEY),
                text(context, CLIENT_KEY),
                URI.create(context.setting(BASE_URL).map(String::strip).filter(url -> !url.isEmpty())
                        .orElse(DEFAULT_BASE_URL)));
    }

    boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    boolean hasClientKey() {
        return !clientKey.isEmpty();
    }

    private static String text(PluginContext context, String name) {
        return context.setting(name).map(String::strip).orElse("");
    }
}
