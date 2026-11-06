package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * A result of a search at the provider, from which the user picks the right movie.
 *
 * @param providerId ID at the provider; passed to {@link MetadataScraper#fetch(String)}
 * @param overview   short description or {@code null}
 * @param posterUrl  small preview image or {@code null}
 */
public record SearchResult(
        String providerId,
        String title,
        String originalTitle,
        Integer year,
        String overview,
        URI posterUrl) {

    public SearchResult {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
