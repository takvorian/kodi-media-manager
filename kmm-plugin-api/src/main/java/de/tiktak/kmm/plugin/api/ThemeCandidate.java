package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * A theme music to choose from.
 *
 * @param name            name at the provider, e.g. {@code Main Theme} or the title of a video
 * @param subject         TV show or movie the provider lists the music for, e.g. {@code ALF}; KMM uses it to
 *                        recognize safe matches
 * @param source          address the scraper loads the music from (audio file or video)
 * @param previewUrl      directly playable audio file for listening, or {@code null}
 * @param webUrl          page of the provider to view in the browser
 * @param durationSeconds length in seconds or {@code null}
 */
public record ThemeCandidate(
        String name,
        String subject,
        URI source,
        URI previewUrl,
        URI webUrl,
        Integer durationSeconds) {

    public ThemeCandidate {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(webUrl, "webUrl must not be null");
    }
}
