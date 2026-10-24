package de.tiktak.kmm.plugin.api;

import java.util.Map;
import java.util.Objects;

/**
 * Ergebnis eines Metadaten-Scrapers. Felder, die der Scraper nicht kennt, sind {@code null}.
 */
public record MetadataResult(
        String title,
        String originalTitle,
        Integer year,
        String plot,
        Map<String, String> uniqueIds) {

    public MetadataResult {
        Objects.requireNonNull(title, "title must not be null");
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
    }
}
