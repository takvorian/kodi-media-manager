package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * Serien-Scraper auf Basis von TheTVDB (API v4) mit offizieller, DVD- oder absoluter
 * Episodenreihenfolge. TVDB verlangt einen sichtbaren Hinweis auf die Datenquelle.
 */
@Extension
public class TvdbTvShowScraper implements TvShowScraper {

    static final String ID = "tvdb";
    static final String DISPLAY_NAME = "TheTVDB";
    static final String MISSING_API_KEY = "Für TVDB fehlt ein API-Key (Einstellungen → API-Keys).";
    static final Attribution ATTRIBUTION = new Attribution(
            "Metadata provided by TheTVDB. Please consider adding missing information or subscribing.",
            URI.create("https://www.thetvdb.com"));

    private final Clock clock;
    private TvdbSettings settings;
    private TvdbApi api;

    public TvdbTvShowScraper() {
        this(Clock.systemUTC());
    }

    TvdbTvShowScraper(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.TV_SHOW);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = TvdbSettings.from(context);
        api = new TvdbApi(new TvdbClient(context.httpClient(), settings, context.userAgent(), clock), settings);
    }

    @Override
    public Optional<String> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    @Override
    public Optional<Attribution> attribution() {
        return Optional.of(ATTRIBUTION);
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        TvdbApi tvdb = api();
        if (!supports(query.mediaType())) {
            return List.of();
        }
        TvdbMapper mapper = new TvdbMapper(settings);
        return tvdb.searchSeries(query.title(), query.year()).stream().map(mapper::toSearchResult).toList();
    }

    @Override
    public Optional<TvShowResult> fetch(String providerId) {
        TvdbApi tvdb = api();
        return tvdb.series(providerId).map(series -> new TvdbMapper(settings).toShow(series,
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

    /** Über bekannte TVDB-, IMDb- oder TMDB-ID, sonst der eindeutig passende Suchtreffer. */
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

    /**
     * @throws ScraperException wenn der API-Key fehlt
     */
    private TvdbApi api() {
        if (api == null) {
            throw new IllegalStateException("TVDB scraper has not been initialized");
        }
        missingConfiguration().ifPresent(message -> {
            throw new ScraperException(message);
        });
        return api;
    }

    private static String normalize(String title) {
        return title.strip().toLowerCase(Locale.ROOT);
    }
}
