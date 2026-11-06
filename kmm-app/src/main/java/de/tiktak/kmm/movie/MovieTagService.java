package de.tiktak.kmm.movie;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tags of movies: count, find and change. Changed tags end up in the NFO and database as when saving the metadata;
 * Kodi then re-reads the movie.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieTagService {

    private final MovieRepository movieRepository;
    private final MovieSupplementWriter supplementWriter;
    private final MovieNfoSync nfoSync;

    /** @return the number of movies per tag */
    public Map<String, Long> countByTag() {
        return movieRepository.countByTag().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    public List<Long> findIdsByTag(String tag) {
        return movieRepository.findIdsByTag(tag);
    }

    /** @return ID and title of the movies with the tag, sorted by title */
    public Map<Long, String> findTitlesByTag(String tag) {
        return movieRepository.findTitlesByTag(tag).stream().collect(Collectors.toMap(row -> (Long) row[0],
                row -> (String) row[1], (first, second) -> first, LinkedHashMap::new));
    }

    /**
     * @param change maps the previous tags to the new ones
     * @return whether the tags have changed
     * @throws MovieNotFoundException  if the movie does not exist
     * @throws NfoNotWritableException if the NFO cannot be written; the database then remains unchanged
     */
    @Transactional
    public boolean changeTags(long movieId, UnaryOperator<List<String>> change) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        nfoSync.refreshIfChangedOutside(movie);
        MovieSupplement current = movie.supplement();
        MovieSupplement changed = current.withTags(change.apply(current.tags()));
        if (changed.tags().equals(current.tags())) {
            return false;
        }
        supplementWriter.write(movie, changed);
        movie.applySupplement(changed, movie.getMovieSet());
        log.info("Tags of movie {} changed to {}", movieId, changed.tags());
        return true;
    }
}
