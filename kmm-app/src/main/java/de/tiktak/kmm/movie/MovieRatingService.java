package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.rating.RatingFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ergänzt fehlende Bewertungen weiterer Anbieter (etwa Trakt) in Bibliothek und NFO. Vorhandene Bewertungen bleiben.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRatingService {

    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";

    private final MovieRepository movieRepository;
    private final MovieNfoSync nfoSync;
    private final MovieSupplementWriter supplementWriter;
    private final RatingFinder ratingFinder;

    /**
     * @return ob eine Bewertung ergänzt wurde
     * @throws MovieNotFoundException  wenn es den Film nicht gibt
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann
     */
    @Transactional
    public boolean fillMissing(long movieId) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        nfoSync.refreshIfChangedOutside(movie);
        MovieSupplement current = movie.supplement();
        Set<String> present = current.ratings().stream().map(MovieSupplement.Rating::source)
                .collect(Collectors.toSet());
        MovieMetadata metadata = movie.metadata();
        List<ProviderRating> found = ratingFinder.findMissing(MediaType.MOVIE, metadata.title(), metadata.year(),
                uniqueIds(metadata), present);
        if (found.isEmpty()) {
            return false;
        }
        List<MovieSupplement.Rating> ratings = new ArrayList<>(current.ratings());
        found.forEach(rating -> ratings.add(new MovieSupplement.Rating(rating.source(), rating.value(), rating.max(),
                rating.votes(), current.ratings().isEmpty() && ratings.isEmpty())));
        MovieSupplement changed = current.withRatings(ratings);
        supplementWriter.write(movie, changed);
        movie.applySupplement(changed, movie.getMovieSet());
        log.info("Ratings of movie {} added: {}", movieId, found);
        return true;
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
