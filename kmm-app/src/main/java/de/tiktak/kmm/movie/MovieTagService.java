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
 * Tags von Filmen: zählen, finden und ändern. Geänderte Tags landen wie beim Speichern der Metadaten in NFO und
 * Datenbank; Kodi liest den Film danach neu ein.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieTagService {

    private final MovieRepository movieRepository;
    private final MovieSupplementWriter supplementWriter;
    private final MovieNfoSync nfoSync;

    /** @return je Tag die Anzahl der Filme */
    public Map<String, Long> countByTag() {
        return movieRepository.countByTag().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    public List<Long> findIdsByTag(String tag) {
        return movieRepository.findIdsByTag(tag);
    }

    /** @return ID und Titel der Filme mit dem Tag, nach Titel sortiert */
    public Map<Long, String> findTitlesByTag(String tag) {
        return movieRepository.findTitlesByTag(tag).stream().collect(Collectors.toMap(row -> (Long) row[0],
                row -> (String) row[1], (first, second) -> first, LinkedHashMap::new));
    }

    /**
     * @param change bildet die bisherigen Tags auf die neuen ab
     * @return ob sich die Tags geändert haben
     * @throws MovieNotFoundException  wenn es den Film nicht gibt
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann; die Datenbank bleibt dann unverändert
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
