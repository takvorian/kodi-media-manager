package de.tiktak.kmm.movie;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.NfoException;
import lombok.RequiredArgsConstructor;

/**
 * Writes changed further details of a movie (tags, set) to the NFO as when saving the metadata, and reports
 * the change so that Kodi re-reads the movie. If the extras of the NFO are unknown, the file is left untouched,
 * so that nothing KMM does not know about is lost.
 */
@Component
@RequiredArgsConstructor
class MovieSupplementWriter {

    private final MovieNfoFiles nfoFiles;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * @throws NfoNotWritableException if the NFO cannot be written
     */
    void write(Movie movie, MovieSupplement supplement) {
        if (movie.isSupplementKnown()) {
            try {
                NfoFileState written = nfoFiles.write(movie.companionFiles(), movie.metadata(),
                        MovieNfoMapper.toNfoSupplement(supplement, movie.streamDetails(), movie.getAddedAt(),
                                clock.getZone(), movie.nfoTrailer()));
                movie.markNfoSynchronized(written.modifiedAt());
            } catch (NfoException e) {
                throw new NfoNotWritableException(e);
            }
        }
        events.publishEvent(new MovieFilesChangedEvent(movie.getId()));
    }

    /**
     * Writes the movie's trailer link; in an existing NFO only {@code <trailer>} changes. Without an NFO, one is
     * created as with {@link #write}.
     *
     * @throws NfoNotWritableException if the NFO cannot be written
     */
    void writeTrailer(Movie movie) {
        Optional<NfoFileState> nfo = nfoFiles.find(movie.companionFiles());
        if (nfo.isEmpty()) {
            write(movie, movie.supplement());
            return;
        }
        try {
            movie.markNfoSynchronized(nfoFiles.writeTrailer(nfo.get(), movie.getTrailer()).modifiedAt());
        } catch (NfoException e) {
            throw new NfoNotWritableException(e);
        }
        events.publishEvent(new MovieFilesChangedEvent(movie.getId()));
    }
}
