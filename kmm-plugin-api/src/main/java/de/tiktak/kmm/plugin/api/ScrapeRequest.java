package de.tiktak.kmm.plugin.api;

import java.util.Map;
import java.util.Objects;

/**
 * Anfrage an einen Scraper.
 *
 * @param mediaType Art des gesuchten Mediums
 * @param title     Suchtitel
 * @param year      Erscheinungsjahr oder {@code null}, falls unbekannt
 * @param uniqueIds bekannte IDs je Anbieter, z. B. {@code imdb -> tt0133093}
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
