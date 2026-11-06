package de.tiktak.kmm.plugin.trakt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import tools.jackson.databind.JsonNode;

/**
 * Public community rating from Trakt.tv as rating {@code trakt}, like Ember's Trakt scraper. The entry is found via
 * the IMDb ID, otherwise via the TMDB or TVDB ID.
 */
@Extension
public class TraktEnrichmentScraper implements EnrichmentScraper {

    static final String ID = "trakt";
    static final String RATING_SOURCE = "trakt";
    static final int MAX_RATING = 10;
    /** Name of the provider in messages for the user. */
    static final String PROVIDER = "Trakt";
    static final LocalizedText MISSING_CLIENT_ID = LocalizedText.of("scraper.trakt.clientIdMissing");
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,10}");

    private TraktSettings settings;
    private TraktClient client;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Trakt";
    }

    @Override
    public Set<String> ratingSources() {
        return Set.of(RATING_SOURCE);
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = TraktSettings.from(context);
        client = new TraktClient(context.httpClient(), settings, context.userAgent());
    }

    @Override
    public Optional<LocalizedText> missingConfiguration() {
        return settings != null && settings.hasClientId() ? Optional.empty() : Optional.of(MISSING_CLIENT_ID);
    }

    @Override
    public Optional<Enrichment> findEnrichment(ScrapeRequest request) {
        if (client == null) {
            throw new IllegalStateException("Trakt scraper has not been initialized");
        }
        if (missingConfiguration().isPresent() || !supports(request.mediaType())) {
            return Optional.empty();
        }
        String type = request.mediaType() == MediaType.MOVIE ? "movies" : "shows";
        return traktId(request)
                .flatMap(id -> client.get("/%s/%s/ratings".formatted(type, id)))
                .flatMap(TraktEnrichmentScraper::toRating)
                .map(rating -> Enrichment.ofRatings(List.of(rating)));
    }

    /** Trakt takes the IMDb ID directly; TMDB and TVDB IDs are translated into the Trakt ID via the search. */
    private Optional<String> traktId(ScrapeRequest request) {
        Map<String, String> ids = request.uniqueIds();
        String imdb = ids.get("imdb");
        if (imdb != null && IMDB_ID.matcher(imdb).matches()) {
            return Optional.of(imdb);
        }
        boolean movie = request.mediaType() == MediaType.MOVIE;
        return lookUp(movie ? "tmdb" : "tvdb", ids.get(movie ? "tmdb" : "tvdb"), movie ? "movie" : "show")
                .or(() -> movie ? Optional.empty() : lookUp("tmdb", ids.get("tmdb"), "show"));
    }

    private Optional<String> lookUp(String source, String id, String type) {
        if (id == null || !NUMERIC_ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return client.get("/search/%s/%s?type=%s".formatted(source, id, type))
                .flatMap(results -> results.valueStream().findFirst())
                .map(result -> result.path(type).path("ids").path("trakt"))
                .filter(JsonNode::isNumber)
                .map(JsonNode::asString);
    }

    private static Optional<ProviderRating> toRating(JsonNode ratings) {
        int votes = ratings.path("votes").asInt(0);
        double rating = ratings.path("rating").asDouble(0);
        if (votes <= 0 || rating <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ProviderRating(RATING_SOURCE, Math.round(rating * 10) / 10.0, MAX_RATING, votes));
    }
}
