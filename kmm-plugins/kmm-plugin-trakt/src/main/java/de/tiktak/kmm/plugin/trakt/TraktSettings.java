package de.tiktak.kmm.plugin.trakt;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.trakt.*}).
 *
 * @param clientId Client-ID einer Trakt-App; leer, wenn nicht eingerichtet
 * @param baseUrl  Basis-URL der API
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
