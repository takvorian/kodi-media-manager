package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * Suche nach einem Medium beim Anbieter.
 *
 * @param year Erscheinungsjahr zur Eingrenzung oder {@code null}
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
