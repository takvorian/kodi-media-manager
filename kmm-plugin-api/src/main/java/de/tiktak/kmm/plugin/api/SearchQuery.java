package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * Search for a medium at the provider.
 *
 * @param year release year to narrow the search, or {@code null}
 */
public record SearchQuery(MediaType mediaType, String title, Integer year) {

    public SearchQuery {
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        title = title.strip();
    }
}
