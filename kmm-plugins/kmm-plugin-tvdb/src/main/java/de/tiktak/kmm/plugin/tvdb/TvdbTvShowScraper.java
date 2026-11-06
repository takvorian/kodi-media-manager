package de.tiktak.kmm.plugin.tvdb;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * TV show scraper based on TheTVDB (API v4) with official, DVD or absolute episode order.
 */
@Extension
public class TvdbTvShowScraper extends AbstractTvdbScraper implements TvShowScraper {

    public TvdbTvShowScraper() {
        this(Clock.systemUTC());
    }

    TvdbTvShowScraper(Clock clock) {
        super(clock);
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        TvdbApi tvdb = api();
        if (!supports(query.mediaType())) {
            return List.of();
        }
        TvdbMapper mapper = new TvdbMapper(settings());
        return tvdb.searchSeries(query.title(), query.year()).stream().map(mapper::toSearchResult).toList();
    }

    @Override
    public Optional<TvShowResult> fetch(String providerId) {
        TvdbApi tvdb = api();
        return tvdb.series(providerId).map(series -> new TvdbMapper(settings()).toShow(series,
                tvdb.seriesTranslation(providerId), tvdb.episodes(providerId, false), tvdb.episodes(providerId, true)));
    }

    @Override
    public Optional<TvShowResult> scrape(ScrapeRequest request) {
        TvdbApi tvdb = api();
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return tvdbId(tvdb, request).flatMap(this::fetch);
    }

    /** TVDB only supplies directors, writers and guest stars in the details of every single episode. */
    @Override
    public EpisodeResult episodeDetails(EpisodeResult episode) {
        String tvdbId = episode.uniqueIds().get(TvdbApi.TVDB);
        if (tvdbId == null) {
            return episode;
        }
        return api().episode(tvdbId).map(details -> new TvdbMapper(settings()).withCrew(episode, details))
                .orElse(episode);
    }

    /** Via a known TVDB, IMDb or TMDB ID, otherwise the unambiguously matching search result. */
    private Optional<String> tvdbId(TvdbApi tvdb, ScrapeRequest request) {
        Optional<String> known = tvdb.seriesIdOf(request.uniqueIds());
        if (known.isPresent()) {
            return known;
        }
        String title = normalize(request.title());
        return search(new SearchQuery(request.mediaType(), request.title(), request.year())).stream()
                .filter(result -> title.equals(normalize(result.title()))
                        || (result.originalTitle() != null && title.equals(normalize(result.originalTitle()))))
                .filter(result -> request.year() == null || request.year().equals(result.year()))
                .findFirst()
                .map(SearchResult::providerId);
    }

    private static String normalize(String title) {
        return title.strip().toLowerCase(Locale.ROOT);
    }
}
