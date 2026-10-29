package de.tiktak.kmm.movie;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.enrichment.EnrichmentFinder;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ergänzt Filme über die Ergänzungs-Scraper (etwa Trakt, OMDb) um Bewertungen und füllt leere Felder. Vorhandene
 * Angaben bleiben; vorhandene Bewertungen ändern sich nur beim Aktualisieren.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieEnrichmentService {

    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";
    private static final EnrichmentFinder.RatingAdapter<MovieSupplement.Rating> RATINGS =
            new EnrichmentFinder.RatingAdapter<>() {
                @Override
                public String source(MovieSupplement.Rating rating) {
                    return rating.source();
                }

                @Override
                public boolean isDefault(MovieSupplement.Rating rating) {
                    return rating.defaultRating();
                }

                @Override
                public MovieSupplement.Rating create(ProviderRating rating, boolean defaultRating) {
                    return new MovieSupplement.Rating(rating.source(), rating.value(), rating.max(), rating.votes(),
                            defaultRating);
                }
            };

    private final MovieRepository movieRepository;
    private final MovieNfoSync nfoSync;
    private final MovieSupplementWriter supplementWriter;
    private final MovieMetadataService metadataService;
    private final EnrichmentFinder finder;

    /**
     * Ergänzt fehlende Bewertungen und leere Felder.
     *
     * @return ob sich etwas geändert hat
     * @throws MovieNotFoundException  wenn es den Film nicht gibt
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann
     */
    @Transactional
    public boolean fillMissing(long movieId) {
        return enrich(movieId, false);
    }

    /** Wie {@link #fillMissing}, aktualisiert aber auch vorhandene Bewertungen der Anbieter. */
    @Transactional
    public boolean refresh(long movieId) {
        return enrich(movieId, true);
    }

    private boolean enrich(long movieId, boolean refresh) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        nfoSync.refreshIfChangedOutside(movie);
        MovieMetadata metadata = movie.metadata();
        MovieSupplement supplement = movie.supplement();
        Set<String> present = supplement.ratings().stream().map(MovieSupplement.Rating::source)
                .collect(Collectors.toSet());
        List<Enrichment> found = finder.find(MediaType.MOVIE, metadata.title(), metadata.year(), uniqueIds(metadata),
                EnrichmentFinder.contributes(refresh, present, hasEmptyFields(metadata, supplement)));
        MovieMetadata enrichedMetadata = withEmptyFieldsFilled(metadata, found);
        MovieSupplement enrichedSupplement = withEmptyFieldsFilled(supplement, found)
                .withRatings(EnrichmentFinder.mergeRatings(supplement.ratings(), found, refresh, RATINGS));
        if (enrichedMetadata.equals(metadata) && enrichedSupplement.equals(supplement)) {
            return false;
        }
        movie.applySupplement(enrichedSupplement, movie.getMovieSet());
        if (enrichedMetadata.equals(metadata)) {
            supplementWriter.write(movie, enrichedSupplement);
        } else {
            metadataService.update(movieId, enrichedMetadata);
        }
        log.info("Movie {} enriched{}", movieId, refresh ? " and ratings refreshed" : "");
        return true;
    }

    static boolean hasEmptyFields(MovieMetadata metadata, MovieSupplement supplement) {
        return metadata.plot() == null || metadata.runtime() == null || metadata.premiered() == null
                || metadata.mpaa() == null || metadata.genres().isEmpty() || supplement.directors().isEmpty()
                || supplement.writers().isEmpty() || supplement.actors().isEmpty()
                || supplement.countries().isEmpty();
    }

    private static MovieMetadata withEmptyFieldsFilled(MovieMetadata metadata, List<Enrichment> found) {
        LocalDate premiered = orFirst(metadata.premiered(), found, Enrichment::premiered);
        return new MovieMetadata(metadata.title(), metadata.originalTitle(), metadata.sortTitle(),
                metadata.year() != null || premiered == null ? metadata.year() : Integer.valueOf(premiered.getYear()),
                premiered,
                orFirst(metadata.plot(), found, Enrichment::plot),
                metadata.outline(), metadata.tagline(),
                orFirst(metadata.runtime(), found, Enrichment::runtime),
                orFirst(metadata.mpaa(), found, Enrichment::certification),
                metadata.genres().isEmpty() ? orEmpty(EnrichmentFinder.first(found, Enrichment::genres))
                        : metadata.genres(),
                metadata.imdbId(), metadata.tmdbId(), metadata.edition(), metadata.videoSource()).withinLimits();
    }

    private static MovieSupplement withEmptyFieldsFilled(MovieSupplement supplement, List<Enrichment> found) {
        List<MovieSupplement.Actor> actors = supplement.actors().isEmpty()
                ? orEmpty(EnrichmentFinder.first(found, Enrichment::actors)).stream()
                        .map(name -> new MovieSupplement.Actor(name, null, null)).toList()
                : supplement.actors();
        return new MovieSupplement(actors,
                orList(supplement.directors(), found, Enrichment::directors),
                orList(supplement.writers(), found, Enrichment::writers),
                supplement.studios(),
                orList(supplement.countries(), found, Enrichment::countries),
                supplement.tags(), supplement.ratings(), supplement.set(), supplement.userRating(),
                supplement.playCount(), supplement.lastPlayed());
    }

    private static <V> V orFirst(V current, List<Enrichment> found, Function<Enrichment, V> value) {
        return current != null ? current : EnrichmentFinder.first(found, value);
    }

    private static List<String> orList(List<String> current, List<Enrichment> found,
            Function<Enrichment, List<String>> value) {
        return current.isEmpty() ? orEmpty(EnrichmentFinder.first(found, value)) : current;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static Map<String, String> uniqueIds(MovieMetadata metadata) {
        Map<String, String> ids = new LinkedHashMap<>();
        if (metadata.imdbId() != null) {
            ids.put(IMDB, metadata.imdbId());
        }
        if (metadata.tmdbId() != null) {
            ids.put(TMDB, metadata.tmdbId());
        }
        return ids;
    }
}
