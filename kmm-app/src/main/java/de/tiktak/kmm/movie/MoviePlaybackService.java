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
 * Reads the playback state and own rating of movies and takes over new ones, for example from Kodi. The state is
 * written to the
 * existing NFO and to the database; without an NFO only to the database. Unlike when
 * saving the metadata, no {@link MovieFilesChangedEvent} is published: the state comes from
 * Kodi, so Kodi does not need to rescan the movie.
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
     * @param playCount    number of playbacks or {@code null}
     * @param lastPlayedAt last playback or {@code null}
     * @param userRating   own rating 1-10 or {@code null}
     * @throws NfoNotWritableException if the NFO cannot be written; the database
     *                                 then stays unchanged
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
