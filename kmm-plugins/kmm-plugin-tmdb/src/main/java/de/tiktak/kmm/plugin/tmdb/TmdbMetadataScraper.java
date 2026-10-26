package de.tiktak.kmm.plugin.tmdb;

import java.util.List;
import java.util.Optional;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;

/**
 * Metadaten-Scraper für Filme auf Basis von The Movie Database (TMDB).
 */
@Extension
public class TmdbMetadataScraper extends AbstractTmdbScraper implements MetadataScraper {

    @Override
    public List<SearchResult> search(SearchQuery query) {
        TmdbApi api = api();
        if (!supports(query.mediaType())) {
            return List.of();
        }
        TmdbMovieMapper mapper = mapper();
        return api.searchMovies(query.title(), query.year())
                .map(response -> JsonNodes.elements(response.path("results")).map(mapper::toSearchResult).toList())
                .orElse(List.of());
    }

    @Override
    public Optional<MetadataResult> fetch(String providerId) {
        return api().movie(providerId).map(mapper()::toMetadata);
    }

    @Override
    public Optional<MetadataResult> scrape(ScrapeRequest request) {
        TmdbApi api = api();
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return tmdbId(api, request).flatMap(this::fetch);
    }

    /** Über bekannte TMDB- oder IMDb-ID, sonst der eindeutig passende Suchtreffer. */
    private Optional<String> tmdbId(TmdbApi api, ScrapeRequest request) {
        if (request.uniqueIds().containsKey(TmdbApi.TMDB) || request.uniqueIds().containsKey(TmdbApi.IMDB)) {
            return api.tmdbIdOf(request.uniqueIds());
        }
        List<SearchResult> results = search(new SearchQuery(request.mediaType(), request.title(), request.year()));
        return SearchMatches.bestMatch(results, request).map(SearchResult::providerId);
    }

    private TmdbMovieMapper mapper() {
        return new TmdbMovieMapper(settings());
    }
}
