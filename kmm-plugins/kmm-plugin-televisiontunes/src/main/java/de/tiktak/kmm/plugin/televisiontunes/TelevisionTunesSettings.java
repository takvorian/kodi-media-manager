package de.tiktak.kmm.plugin.televisiontunes;

import java.net.URI;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.televisiontunes.*}).
 *
 * @param baseUrl address of the site; TelevisionTunes can only be reached via HTTP
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
