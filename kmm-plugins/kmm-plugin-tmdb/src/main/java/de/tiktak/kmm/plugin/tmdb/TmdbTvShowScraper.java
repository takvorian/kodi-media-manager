package de.tiktak.kmm.plugin.tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.EpisodeOrder;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import tools.jackson.databind.JsonNode;

/**
 * TV show scraper based on TMDB. Besides the aired order TMDB knows episode groups, such as a streaming service's
 * order, which can be chosen per TV show; the setting for DVD or absolute order has no effect here.
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
        return fetch(providerId, null);
    }

    /**
     * @param episodeOrder the ID of an episode group, or {@code null} or {@code aired} for the aired order
     */
    @Override
    public Optional<TvShowResult> fetch(String providerId, String episodeOrder) {
        TmdbApi api = api();
        Optional<JsonNode> group = Optional.empty();
        if (episodeOrder != null && !TmdbEpisodeGroups.AIRED.equals(episodeOrder)) {
            if (!TmdbEpisodeGroups.isGroupId(episodeOrder)) {
                throw new IllegalArgumentException("Unknown TMDB episode group: " + episodeOrder);
            }
            group = api.tvEpisodeGroup(episodeOrder);
            if (group.isEmpty()) {
                return Optional.empty();
            }
        }
        Optional<JsonNode> episodeGroup = group;
        return api.tvShow(providerId).map(show -> {
            TvShowResult aired = mapper().toShow(show, seasons(api, providerId, show));
            return episodeGroup.map(chosen -> TmdbEpisodeGroups.renumber(aired, chosen)).orElse(aired);
        });
    }

    @Override
    public List<EpisodeOrder> episodeOrders(String providerId) {
        return api().tvEpisodeGroups(providerId).map(TmdbEpisodeGroups::orders).orElse(List.of());
    }

    @Override
    public Optional<TvShowResult> scrape(ScrapeRequest request) {
        TmdbApi api = api();
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return tmdbId(api, request).flatMap(this::fetch);
    }

    /** Via a known TMDB, TVDB or IMDb ID, otherwise the unambiguously matching search result. */
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
