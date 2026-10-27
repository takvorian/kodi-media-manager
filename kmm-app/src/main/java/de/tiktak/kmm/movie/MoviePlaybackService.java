package de.tiktak.kmm.movie;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.NfoPlayback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest Wiedergabestand und eigene Bewertung von Filmen und übernimmt neue, etwa aus Kodi. Der Stand wird in die
 * vorhandene NFO und in die Datenbank geschrieben; ohne NFO nur in die Datenbank. Anders als beim
 * Speichern der Metadaten wird kein {@link MovieFilesChangedEvent} veröffentlicht: Der Stand kommt von
 * Kodi, Kodi muss den Film deshalb nicht neu einlesen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoviePlaybackService {

    private final MovieRepository movieRepository;
    private final MovieNfoFiles nfoFiles;
    private final Clock clock;

    public List<MoviePlayback> findBySource(long sourceId) {
        return movieRepository.findBySourceId(sourceId).stream().map(MoviePlayback::of).toList();
    }

    public MoviePlayback find(long movieId) {
        return MoviePlayback.of(require(movieId));
    }

    /**
     * @param playCount    Anzahl der Wiedergaben oder {@code null}
     * @param lastPlayedAt letzte Wiedergabe oder {@code null}
     * @param userRating   eigene Bewertung 1–10 oder {@code null}
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann; die Datenbank bleibt
     *                                 dann unverändert
     */
    @Transactional
    public void changePlayback(long movieId, Integer playCount, Instant lastPlayedAt, Integer userRating) {
        Movie movie = require(movieId);
        nfoFiles.find(movie.companionFiles()).ifPresent(nfoFile -> {
            try {
                NfoFileState written = nfoFiles.writePlayback(nfoFile, new NfoPlayback(playCount,
                        MovieNfoMapper.toLocal(lastPlayedAt, clock.getZone()), userRating));
                movie.markNfoSynchronized(written.modifiedAt());
            } catch (NfoException e) {
                throw new NfoNotWritableException(e);
            }
        });
        movie.changePlayback(playCount, lastPlayedAt, userRating);
        log.info("Playback of movie {} changed to {} plays, last {}, rating {}", movieId, playCount,
                lastPlayedAt, userRating);
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }
}
