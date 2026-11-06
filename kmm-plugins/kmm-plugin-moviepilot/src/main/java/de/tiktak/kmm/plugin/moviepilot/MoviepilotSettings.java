package de.tiktak.kmm.plugin.moviepilot;

import java.net.URI;
import java.time.Duration;

import de.tiktak.kmm.plugin.api.PluginContext;

/**
 * Settings of the plugin ({@code kmm.plugins.settings.moviepilot.*}).
 *
 * @param baseUrl      address of the site
 * @param requestDelay gap between two requests
 */
record MoviepilotSettings(URI baseUrl, Duration requestDelay) {

    static final String BASE_URL = "base-url";
    static final String REQUEST_DELAY_MS = "request-delay-ms";
    static final String DEFAULT_BASE_URL = "https://www.moviepilot.de";
    static final long DEFAULT_REQUEST_DELAY_MS = 1000;

    static MoviepilotSettings from(PluginContext context) {
        String url = context.setting(BASE_URL).map(String::strip).filter(value -> !value.isEmpty())
                .orElse(DEFAULT_BASE_URL);
        long delay = context.setting(REQUEST_DELAY_MS).map(String::strip).filter(value -> !value.isEmpty())
                .map(Long::parseLong).orElse(DEFAULT_REQUEST_DELAY_MS);
        return new MoviepilotSettings(URI.create(url.replaceAll("/+$", "")), Duration.ofMillis(delay));
    }
}
