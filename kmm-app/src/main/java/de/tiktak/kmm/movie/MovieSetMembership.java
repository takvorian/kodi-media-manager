package de.tiktak.kmm.movie;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ändert die Set-Zugehörigkeit eines Films in Datenbank und NFO, je Film in einer eigenen Transaktion. Kodi kennt
 * nur ein Set je Film; wer einem Set hinzugefügt wird, verlässt sein bisheriges.
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
     * @param setId das neue Set oder {@code null}, um den Film aus seinem Set zu nehmen
     * @return ob sich etwas geändert hat
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

    /** Schreibt die NFO neu, etwa nachdem das Set umbenannt wurde. */
    @Transactional
    public boolean rewrite(long movieId) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        nfoSync.refreshIfChangedOutside(movie);
        supplementWriter.write(movie, movie.supplement());
        return true;
    }
}
