package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

/**
 * Ergebnis eines Metadaten-Scrapers. Felder, die der Scraper nicht kennt, sind {@code null} bzw. leer.
 *
 * @param premiered     Datum der Erstaufführung
 * @param runtime       Laufzeit in Minuten
 * @param certification Altersfreigabe in der Schreibweise für die NFO, z. B. {@code FSK 12}
 * @param uniqueIds     IDs je Anbieter, z. B. {@code imdb -> tt0113277}, {@code tmdb -> 949}
 */
@Builder
public record MetadataResult(
        String title,
        String originalTitle,
        Integer year,
        LocalDate premiered,
        String plot,
        String tagline,
        Integer runtime,
        String certification,
        List<String> genres,
        Map<String, String> uniqueIds) {

    public MetadataResult {
        Objects.requireNonNull(title, "title must not be null");
        genres = genres == null ? List.of() : List.copyOf(genres);
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
    }
}
