package de.tiktak.kmm.movie;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.NfoException;
import lombok.RequiredArgsConstructor;

/**
 * Schreibt geänderte weitere Angaben eines Films (Tags, Set) wie beim Speichern der Metadaten in die NFO und meldet
 * die Änderung, damit Kodi den Film neu einliest. Ist das Beiwerk der NFO unbekannt, bleibt die Datei unberührt,
 * damit nichts verloren geht, was KMM nicht kennt.
 */
@Component
@RequiredArgsConstructor
class MovieSupplementWriter {

    private final MovieNfoFiles nfoFiles;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann
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
}
