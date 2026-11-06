package de.tiktak.kmm.plugin.api;

import java.util.Map;
import java.util.Objects;

/**
 * Request to a scraper.
 *
 * @param mediaType type of the medium looked for
 * @param title     search title
 * @param year      release year or {@code null} if unknown
 * @param uniqueIds known IDs per provider, e.g. {@code imdb -> tt0133093}
 */
public record ScrapeRequest(MediaType mediaType, String title, Integer year, Map<String, String> uniqueIds) {

    public ScrapeRequest {
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
    }
}
