package de.tiktak.kmm.plugin.televisiontunes;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Einstellungen des Plugins ({@code kmm.plugins.settings.televisiontunes.*}).
 *
 * @param baseUrl Adresse der Seite; TelevisionTunes ist nur über HTTP erreichbar
 */
record TelevisionTunesSettings(URI baseUrl) {

    static final String BASE_URL = "base-url";
    static final String DEFAULT_BASE_URL = "http://televisiontunes.com";

    static TelevisionTunesSettings from(PluginContext context) {
        String url = context.setting(BASE_URL).map(String::strip).filter(value -> !value.isEmpty())
                .orElse(DEFAULT_BASE_URL);
        return new TelevisionTunesSettings(URI.create(url.replaceAll("/+$", "")));
    }

    URI resolve(String path) {
        return URI.create(baseUrl + path);
    }
}
