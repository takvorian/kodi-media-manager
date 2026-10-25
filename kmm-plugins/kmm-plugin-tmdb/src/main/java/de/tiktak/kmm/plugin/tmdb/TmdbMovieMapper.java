package de.tiktak.kmm.plugin.tmdb;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.SearchResult;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Übersetzt TMDB-Antworten in die Typen der Plugin-API.
 */
@RequiredArgsConstructor
class TmdbMovieMapper {

    static final String IMDB = "imdb";
    static final String TMDB = "tmdb";

    private final TmdbSettings settings;

    SearchResult toSearchResult(JsonNode movie) {
        return new SearchResult(
                movie.path("id").asString(),
                text(movie, "title").orElse(text(movie, "original_title").orElse("")),
                text(movie, "original_title").orElse(null),
                releaseDate(movie).map(LocalDate::getYear).orElse(null),
                text(movie, "overview").orElse(null),
                text(movie, "poster_path").map(this::posterUrl).orElse(null));
    }

    MetadataResult toMetadata(JsonNode movie) {
        Optional<LocalDate> releaseDate = releaseDate(movie);
        return MetadataResult.builder()
                .title(text(movie, "title").orElse(text(movie, "original_title").orElse("")))
                .originalTitle(text(movie, "original_title").orElse(null))
                .year(releaseDate.map(LocalDate::getYear).orElse(null))
                .premiered(releaseDate.orElse(null))
                .plot(text(movie, "overview").orElse(null))
                .tagline(text(movie, "tagline").orElse(null))
                .runtime(positiveNumber(movie, "runtime"))
                .certification(certification(movie).orElse(null))
                .genres(genres(movie))
                .uniqueIds(uniqueIds(movie))
                .build();
    }

    private Optional<String> certification(JsonNode movie) {
        return JsonNodes.elements(movie.path("release_dates").path("results"))
                .filter(country -> settings.certificationCountry().equalsIgnoreCase(country.path("iso_3166_1").asString()))
                .flatMap(country -> JsonNodes.elements(country.path("release_dates")))
                .map(release -> release.path("certification").asString().strip())
                .filter(certification -> !certification.isEmpty())
                .findFirst()
                .map(certification -> settings.certificationPrefix() + certification);
    }

    private static List<String> genres(JsonNode movie) {
        return JsonNodes.elements(movie.path("genres"))
                .map(genre -> genre.path("name").asString().strip())
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static Map<String, String> uniqueIds(JsonNode movie) {
        Map<String, String> ids = new LinkedHashMap<>();
        text(movie, "imdb_id").ifPresent(imdbId -> ids.put(IMDB, imdbId));
        ids.put(TMDB, movie.path("id").asString());
        return ids;
    }

    private URI posterUrl(String posterPath) {
        return URI.create(settings.imageBaseUrl().toString().replaceAll("/+$", "") + posterPath);
    }

    private static Optional<LocalDate> releaseDate(JsonNode movie) {
        return text(movie, "release_date").flatMap(TmdbMovieMapper::parseDate);
    }

    private static Optional<LocalDate> parseDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Integer positiveNumber(JsonNode node, String field) {
        int value = node.path(field).asInt(0);
        return value > 0 ? value : null;
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asString().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
