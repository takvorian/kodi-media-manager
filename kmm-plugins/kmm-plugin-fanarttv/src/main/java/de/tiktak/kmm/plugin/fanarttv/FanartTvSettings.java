package de.tiktak.kmm.plugin.fanarttv;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.fanarttv.*}).
 *
 * @param apiKey    project API key of fanart.tv; empty if not configured
 * @param clientKey optional personal key; new images show up earlier with it
 * @param baseUrl   base URL of the API
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
