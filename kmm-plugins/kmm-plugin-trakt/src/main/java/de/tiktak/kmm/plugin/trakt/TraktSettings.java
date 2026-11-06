package de.tiktak.kmm.plugin.trakt;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.trakt.*}).
 *
 * @param clientId client ID of a Trakt app; empty if not set up
 * @param baseUrl  base URL of the API
 */
record TraktSettings(String clientId, URI baseUrl) {

    static final String CLIENT_ID = "client-id";
    static final String BASE_URL = "base-url";
    static final String DEFAULT_BASE_URL = "https://api.trakt.tv";

    static TraktSettings from(PluginContext context) {
        String url = context.setting(BASE_URL).map(String::strip).filter(value -> !value.isEmpty())
                .orElse(DEFAULT_BASE_URL);
        return new TraktSettings(context.setting(CLIENT_ID).map(String::strip).orElse(""),
                URI.create(url.replaceAll("/+$", "")));
    }

    boolean hasClientId() {
        return !clientId.isEmpty();
    }
}
