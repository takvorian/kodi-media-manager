package de.tiktak.kmm.tvshow;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.enrichment.EnrichmentFinder;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import lombok.RequiredArgsConstructor;

/**
 * Enriches TV shows with ratings via the enrichment scrapers (such as Trakt, OMDb) and fills empty fields of the
 * {@code tvshow.nfo}. Existing values are kept; existing ratings only change on refresh.
 */
@Service
@RequiredArgsConstructor
public class TvShowEnrichmentService {

    private static final EnrichmentFinder.RatingAdapter<TvRating> RATINGS = new EnrichmentFinder.RatingAdapter<>() {
        @Override
        public String source(TvRating rating) {
            return rating.source();
        }

        @Override
        public boolean isDefault(TvRating rating) {
            return rating.defaultRating();
        }

        @Override
        public TvRating create(ProviderRating rating, boolean defaultRating) {
            return new TvRating(rating.source(), rating.value(), rating.max(), rating.votes(), defaultRating);
        }
    };

    private final TvShowMetadataService metadataService;
    private final EnrichmentFinder finder;

    /**
     * Adds missing ratings and fills empty fields.
     *
     * @return whether anything changed
     * @throws TvShowProblemException if the TV show does not exist or the NFO cannot be written
     */
    public boolean fillMissing(long showId) {
        return metadataService.changeContent(showId, content -> enrich(content, false));
    }

    /** Like {@link #fillMissing}, but also refreshes existing ratings from the providers. */
    public boolean refresh(long showId) {
        return metadataService.changeContent(showId, content -> enrich(content, true));
    }

    private TvShowContent enrich(TvShowContent content, boolean refresh) {
        TvShowMetadata metadata = content.metadata();
        TvShowExtras extras = content.extras();
        Set<String> present = extras.ratings().stream().map(TvRating::source).collect(Collectors.toSet());
        List<Enrichment> found = finder.find(MediaType.TV_SHOW, metadata.title(), metadata.year(),
                uniqueIds(metadata), EnrichmentFinder.contributes(refresh, present, hasEmptyFields(metadata, extras)));
        if (found.isEmpty()) {
            return content;
        }
        List<TvActor> actors = extras.actors().isEmpty()
                ? orEmpty(EnrichmentFinder.first(found, Enrichment::actors)).stream()
                        .map(name -> new TvActor(name, null, null)).toList()
                : extras.actors();
        TvShowExtras enrichedExtras = new TvShowExtras(actors,
                EnrichmentFinder.mergeRatings(extras.ratings(), found, refresh, RATINGS), extras.seasonNames());
        return new TvShowContent(withEmptyFieldsFilled(metadata, found), enrichedExtras);
    }

    static boolean hasEmptyFields(TvShowMetadata metadata, TvShowExtras extras) {
        return metadata.plot() == null || metadata.runtime() == null || metadata.premiered() == null
                || metadata.mpaa() == null || metadata.genres().isEmpty() || extras.actors().isEmpty();
    }

    private static TvShowMetadata withEmptyFieldsFilled(TvShowMetadata metadata, List<Enrichment> found) {
        LocalDate premiered = orFirst(metadata.premiered(), found, Enrichment::premiered);
        return new TvShowMetadata(metadata.title(), metadata.originalTitle(), metadata.sortTitle(),
                metadata.year() != null || premiered == null ? metadata.year() : Integer.valueOf(premiered.getYear()),
                premiered,
                orFirst(metadata.plot(), found, Enrichment::plot),
                orFirst(metadata.runtime(), found, Enrichment::runtime),
                orFirst(metadata.mpaa(), found, Enrichment::certification),
                metadata.status(),
                metadata.genres().isEmpty() ? orEmpty(EnrichmentFinder.first(found, Enrichment::genres))
                        : metadata.genres(),
                metadata.studios(), metadata.tags(), metadata.tvdbId(), metadata.tmdbId(), metadata.imdbId(),
                metadata.userRating()).withinLimits();
    }

    private static <V> V orFirst(V current, List<Enrichment> found, Function<Enrichment, V> value) {
        return current != null ? current : EnrichmentFinder.first(found, value);
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static Map<String, String> uniqueIds(TvShowMetadata metadata) {
        Map<String, String> ids = new LinkedHashMap<>();
        putIfPresent(ids, TvNfoMapper.IMDB, metadata.imdbId());
        putIfPresent(ids, TvNfoMapper.TVDB, metadata.tvdbId());
        putIfPresent(ids, TvNfoMapper.TMDB, metadata.tmdbId());
        return ids;
    }

    private static void putIfPresent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.put(type, value);
        }
    }
}
