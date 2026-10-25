package de.tiktak.kmm.plugin.tmdb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Die von KMM genutzten TMDB-Endpunkte. Prüft IDs streng, bevor sie im URL-Pfad landen.
 */
@RequiredArgsConstructor
class TmdbApi {

    static final String IMDB = "imdb";
    static final String TMDB = "tmdb";
    private static final Pattern TMDB_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");
    private static final String NO_LANGUAGE = "null";
    private static final String FALLBACK_IMAGE_LANGUAGE = "en";

    private final TmdbClient client;
    private final TmdbSettings settings;

    Optional<JsonNode> searchMovies(String title, Integer year) {
        Map<String, String> parameters = new HashMap<>(Map.of(
                "query", title,
                "language", settings.language(),
                "include_adult", "false"));
        if (year != null) {
            parameters.put("year", year.toString());
        }
        return client.get("/search/movie", parameters);
    }

    Optional<JsonNode> movie(String tmdbId) {
        if (!isTmdbId(tmdbId)) {
            return Optional.empty();
        }
        return client.get("/movie/" + tmdbId, Map.of(
                "language", settings.language(),
                "append_to_response", "release_dates"));
    }

    /** Bilder in der eingestellten Sprache, auf Englisch und ohne Sprache (z. B. Hintergründe). */
    Optional<JsonNode> movieImages(String tmdbId) {
        if (!isTmdbId(tmdbId)) {
            return Optional.empty();
        }
        String languages = String.join(",", settings.imageLanguage(), FALLBACK_IMAGE_LANGUAGE, NO_LANGUAGE);
        return client.get("/movie/" + tmdbId + "/images", Map.of("include_image_language", languages));
    }

    /** TMDB-ID aus bekannten IDs: direkt oder über die IMDb-ID. */
    Optional<String> tmdbIdOf(Map<String, String> uniqueIds) {
        Optional<String> known = Optional.ofNullable(uniqueIds.get(TMDB));
        if (known.isPresent()) {
            return known;
        }
        return Optional.ofNullable(uniqueIds.get(IMDB)).flatMap(this::findByImdbId);
    }

    private Optional<String> findByImdbId(String imdbId) {
        if (!IMDB_ID.matcher(imdbId).matches()) {
            return Optional.empty();
        }
        return client.get("/find/" + imdbId, Map.of("external_source", "imdb_id"))
                .flatMap(response -> JsonNodes.elements(response.path("movie_results")).findFirst())
                .map(movie -> movie.path("id").asString());
    }

    private static boolean isTmdbId(String tmdbId) {
        return TMDB_ID.matcher(tmdbId).matches();
    }
}
