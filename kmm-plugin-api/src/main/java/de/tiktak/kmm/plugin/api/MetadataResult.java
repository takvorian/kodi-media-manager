package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

/**
 * Result of a metadata scraper. Fields the scraper does not know are {@code null} or empty.
 *
 * @param premiered     date of the premiere
 * @param runtime       runtime in minutes
 * @param certification age rating as written in the NFO, e.g. {@code FSK 12}
 * @param uniqueIds     IDs per provider, e.g. {@code imdb -> tt0113277}, {@code tmdb -> 949}
 * @param collection    collection (movie set) the movie belongs to, or {@code null}
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
        Map<String, String> uniqueIds,
        CollectionReference collection) {

    public MetadataResult {
        Objects.requireNonNull(title, "title must not be null");
        genres = genres == null ? List.of() : List.copyOf(genres);
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
    }
}
