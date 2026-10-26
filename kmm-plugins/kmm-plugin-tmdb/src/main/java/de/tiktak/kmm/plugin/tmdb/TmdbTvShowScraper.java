package de.tiktak.kmm.plugin.tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import tools.jackson.databind.JsonNode;

/**
 * Serien-Scraper auf Basis von TMDB. TMDB kennt nur die Reihenfolge der Ausstrahlung; die
 * Einstellung für DVD- oder absolute Reihenfolge wirkt hier nicht.
 */
@Extension
public class TmdbTvShowScraper extends AbstractTmdbScraper implements TvShowScraper {

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.TV_SHOW);
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        TmdbApi api = api();
        if (!supports(query.mediaType())) {
            return List.of();
        }
        TmdbTvMapper mapper = mapper();
        return api.searchTvShows(query.title(), query.year())
                .map(response -> JsonNodes.elements(response.path("results")).map(mapper::toSearchResult).toList())
                .orElse(List.of());
    }

    @Override
    public Optional<TvShowResult> fetch(String providerId) {
        TmdbApi api = api();
        return api.tvShow(providerId).map(show -> mapper().toShow(show, seasons(api, providerId, show)));
    }

    @Override
    public Optional<TvShowResult> scrape(ScrapeRequest request) {
        TmdbApi api = api();
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return tmdbId(api, request).flatMap(this::fetch);
    }

    /** Über bekannte TMDB-, TVDB- oder IMDb-ID, sonst der eindeutig passende Suchtreffer. */
    private Optional<String> tmdbId(TmdbApi api, ScrapeRequest request) {
        Optional<String> known = api.tvShowIdOf(request.uniqueIds());
        if (known.isPresent()) {
            return known;
        }
        List<SearchResult> results = search(new SearchQuery(request.mediaType(), request.title(), request.year()));
        return SearchMatches.bestMatch(results, request).map(SearchResult::providerId);
    }

    private static List<JsonNode> seasons(TmdbApi api, String tmdbId, JsonNode show) {
        List<JsonNode> seasons = new ArrayList<>();
        JsonNodes.elements(show.path("seasons"))
                .map(season -> season.path("season_number").asInt(-1))
                .filter(number -> number >= 0)
                .forEach(number -> api.tvSeason(tmdbId, number).ifPresent(seasons::add));
        return seasons;
    }

    private TmdbTvMapper mapper() {
        return new TmdbTvMapper(settings());
    }
}
