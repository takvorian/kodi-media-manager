package de.tiktak.kmm.plugin.tmdb;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;
import tools.jackson.databind.JsonNode;

/**
 * Metadaten-Scraper für Filme auf Basis von The Movie Database (TMDB).
 */
@Extension
public class TmdbMetadataScraper implements MetadataScraper {

    static final String ID = "tmdb";
    static final String DISPLAY_NAME = "The Movie Database (TMDB)";
    /** IDs landen im URL-Pfad und werden deshalb streng geprüft. */
    private static final Pattern TMDB_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");
    static final String MISSING_API_KEY = "Für TMDB fehlt ein API-Key (Umgebungsvariable KMM_TMDB_API_KEY).";

    private TmdbSettings settings;
    private TmdbClient client;
    private TmdbMovieMapper mapper;

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
        return Set.of(MediaType.MOVIE);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = TmdbSettings.from(context);
        client = new TmdbClient(context.httpClient(), settings, context.userAgent());
        mapper = new TmdbMovieMapper(settings);
    }

    @Override
    public Optional<String> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        requireConfigured();
        if (!supports(query.mediaType())) {
            return List.of();
        }
        Map<String, String> parameters = new HashMap<>(Map.of(
                "query", query.title(),
                "language", settings.language(),
                "include_adult", "false"));
        if (query.year() != null) {
            parameters.put("year", query.year().toString());
        }
        return client.get("/search/movie", parameters)
                .map(response -> JsonNodes.elements(response.path("results")).map(mapper::toSearchResult).toList())
                .orElse(List.of());
    }

    @Override
    public Optional<MetadataResult> fetch(String providerId) {
        requireConfigured();
        if (!TMDB_ID.matcher(providerId).matches()) {
            return Optional.empty();
        }
        return client.get("/movie/" + providerId, Map.of(
                        "language", settings.language(),
                        "append_to_response", "release_dates"))
                .map(mapper::toMetadata);
    }

    @Override
    public Optional<MetadataResult> scrape(ScrapeRequest request) {
        requireConfigured();
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return tmdbId(request).flatMap(this::fetch);
    }

    /** Bekannte TMDB-ID, sonst über die IMDb-ID, sonst der eindeutig passende Suchtreffer. */
    private Optional<String> tmdbId(ScrapeRequest request) {
        Optional<String> known = Optional.ofNullable(request.uniqueIds().get(TmdbMovieMapper.TMDB));
        if (known.isPresent()) {
            return known;
        }
        Optional<String> imdbId = Optional.ofNullable(request.uniqueIds().get(TmdbMovieMapper.IMDB));
        if (imdbId.isPresent()) {
            return findByImdbId(imdbId.get());
        }
        return bestMatch(search(new SearchQuery(request.mediaType(), request.title(), request.year())), request)
                .map(SearchResult::providerId);
    }

    private Optional<String> findByImdbId(String imdbId) {
        if (!IMDB_ID.matcher(imdbId).matches()) {
            return Optional.empty();
        }
        return client.get("/find/" + imdbId, Map.of("external_source", "imdb_id"))
                .flatMap(response -> JsonNodes.elements(response.path("movie_results")).findFirst())
                .map(movie -> movie.path("id").asString());
    }

    /**
     * Nur ein Treffer mit gleichem Titel (bzw. Originaltitel) und passendem Jahr gilt als sicher;
     * lieber kein Ergebnis als ein falsches.
     */
    private static Optional<SearchResult> bestMatch(List<SearchResult> results, ScrapeRequest request) {
        String title = normalize(request.title());
        return results.stream()
                .filter(result -> title.equals(normalize(result.title()))
                        || (result.originalTitle() != null && title.equals(normalize(result.originalTitle()))))
                .filter(result -> request.year() == null || request.year().equals(result.year()))
                .findFirst();
    }

    private void requireConfigured() {
        if (settings == null) {
            throw new IllegalStateException("TMDB scraper has not been initialized");
        }
        missingConfiguration().ifPresent(message -> {
            throw new ScraperException(message);
        });
    }

    private static String normalize(String title) {
        return title.strip().toLowerCase(Locale.ROOT);
    }
}
