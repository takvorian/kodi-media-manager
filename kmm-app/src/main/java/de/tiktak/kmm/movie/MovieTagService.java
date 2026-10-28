package de.tiktak.kmm.movie;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.NfoException;
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
    private final MovieNfoFiles nfoFiles;
    private final Clock clock;
    private final ApplicationEventPublisher events;

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
        MovieSupplement current = movie.supplement();
        MovieSupplement changed = current.withTags(change.apply(current.tags()));
        if (changed.tags().equals(current.tags())) {
            return false;
        }
        if (movie.isSupplementKnown()) {
            try {
                NfoFileState written = nfoFiles.write(movie.companionFiles(), movie.metadata(),
                        MovieNfoMapper.toNfoSupplement(changed, movie.streamDetails(), movie.getAddedAt(),
                                clock.getZone()));
                movie.markNfoSynchronized(written.modifiedAt());
            } catch (NfoException e) {
                throw new NfoNotWritableException(e);
            }
        }
        movie.applySupplement(changed, movie.getMovieSet());
        log.info("Tags of movie {} changed to {}", movieId, changed.tags());
        events.publishEvent(new MovieFilesChangedEvent(movieId));
        return true;
    }
}
