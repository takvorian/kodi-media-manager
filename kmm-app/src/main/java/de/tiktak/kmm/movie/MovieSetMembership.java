package de.tiktak.kmm.movie;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Changes a movie's set membership in the database and NFO, each movie in its own transaction. Kodi knows
 * only one set per movie; a movie added to a set leaves its previous one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieSetMembership {

    private final MovieRepository movieRepository;
    private final MovieSetRepository setRepository;
    private final MovieSupplementWriter supplementWriter;
    private final MovieNfoSync nfoSync;

    /**
     * @param setId the new set or {@code null} to remove the movie from its set
     * @return whether anything has changed
     */
    @Transactional
    public boolean assign(long movieId, Long setId) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        nfoSync.refreshIfChangedOutside(movie);
        MovieSet set = setId == null ? null : setRepository.findById(setId)
                .orElseThrow(() -> MovieSetProblemException.notFound(setId));
        Long current = movie.getMovieSet() == null ? null : movie.getMovieSet().getId();
        if (Objects.equals(current, setId)) {
            return false;
        }
        movie.assignSet(set, null);
        supplementWriter.write(movie, movie.supplement());
        log.info("Movie {} moved from set {} to {}", movieId, current, setId);
        return true;
    }

    /** Rewrites the NFO, for example after the set was renamed. */
    @Transactional
    public boolean rewrite(long movieId) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        nfoSync.refreshIfChangedOutside(movie);
        supplementWriter.write(movie, movie.supplement());
        return true;
    }
}
