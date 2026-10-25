package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * Ein Treffer einer Suche beim Anbieter, aus dem der Nutzer den richtigen Film auswählt.
 *
 * @param providerId ID beim Anbieter; wird an {@link MetadataScraper#fetch(String)} übergeben
 * @param overview   Kurzbeschreibung oder {@code null}
 * @param posterUrl  kleines Vorschaubild oder {@code null}
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
