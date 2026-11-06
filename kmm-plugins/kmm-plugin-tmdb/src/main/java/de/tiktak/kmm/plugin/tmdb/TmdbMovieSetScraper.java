package de.tiktak.kmm.plugin.tmdb;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import de.tiktak.kmm.plugin.api.MovieSetScraper;
import de.tiktak.kmm.plugin.api.SearchResult;
import tools.jackson.databind.JsonNode;

/**
 * Movie sets as collections from The Movie Database (TMDB). If the description is missing in the configured
 * language, it comes in English as in Ember.
 */
@Extension
public class TmdbMovieSetScraper extends AbstractTmdbScraper implements MovieSetScraper {

    static final String FALLBACK_LANGUAGE = "en-US";
    private static final String POSTER_SIZE = "w185";

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE_SET);
    }

    @Override
    public List<SearchResult> searchSets(String title) {
        return api().searchCollections(title)
                .map(response -> JsonNodes.elements(response.path("results")).map(this::toSearchResult).toList())
                .orElse(List.of());
    }

    @Override
    public Optional<MovieSetResult> fetchSet(String providerId) {
        TmdbApi api = api();
        return api.collection(providerId, settings().language()).map(collection -> {
            String overview = text(collection, "overview")
                    .or(() -> api.collection(providerId, FALLBACK_LANGUAGE).flatMap(english -> text(english, "overview")))
                    .orElse(null);
            return new MovieSetResult(providerId, text(collection, "name").orElse(providerId), overview);
        });
    }

    private SearchResult toSearchResult(JsonNode collection) {
        return new SearchResult(collection.path("id").asString(), text(collection, "name").orElse(""),
                text(collection, "original_name").orElse(null), null, text(collection, "overview").orElse(null),
                text(collection, "poster_path").map(path -> settings().imageUrl(POSTER_SIZE, path)).orElse(null));
    }

    private static Optional<String> text(JsonNode node, String field) {
        String value = node.path(field).asString("").strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
