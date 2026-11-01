package de.tiktak.kmm.movie;

import java.time.Clock;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest und ändert die Metadaten eines Films. Änderungen werden zuerst in die NFO geschrieben;
 * schlägt das fehl, bleibt auch die Datenbank unverändert. Die NFO ist die maßgebliche Quelle.
 * Nach dem Speichern wird ein {@link MovieFilesChangedEvent} veröffentlicht.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieMetadataService {

    private final MovieRepository movieRepository;
    private final MovieNfoFiles nfoFiles;
    private final MovieNfoSync nfoSync;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final MediaSourceService sourceService;

    public MovieDetails findDetails(long movieId) {
        Movie movie = require(movieId);
        return MovieDetails.from(movie, nfoFiles.find(movie.companionFiles()));
    }

    /** Der Film für die Filmansicht; eine außerhalb von KMM geänderte NFO wird vorher eingelesen. */
    @Transactional
    public MovieDetails open(long movieId) {
        Movie movie = require(movieId);
        nfoSync.readIfOutdated(movie);
        return MovieDetails.from(movie, nfoFiles.find(movie.companionFiles()));
    }

    @Transactional
    public MovieDetails update(long movieId, MovieMetadata metadata) {
        Movie movie = require(movieId);
        nfoSync.refreshIfChangedOutside(movie);
        NfoFileState written = writeNfo(movie, metadata);
        movie.applyMetadata(metadata);
        movie.markNfoSynchronized(written.modifiedAt());
        log.info("Metadata of movie {} saved to {}", movieId, written.file());
        events.publishEvent(new MovieFilesChangedEvent(movieId));
        return MovieDetails.from(movie, Optional.of(written));
    }

    /**
     * Ändert die Metadaten ausgehend vom aktuellen Stand, etwa in der Stapel-Bearbeitung. Eine außerhalb von KMM
     * geänderte NFO wird vorher eingelesen, damit die Änderung auf ihr aufsetzt.
     *
     * @return ob sich etwas geändert hat
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann
     */
    @Transactional
    public boolean change(long movieId, UnaryOperator<MovieMetadata> change) {
        Movie movie = require(movieId);
        nfoSync.refreshIfChangedOutside(movie);
        MovieMetadata current = movie.metadata();
        MovieMetadata changed = change.apply(current);
        if (changed.equals(current)) {
            return false;
        }
        NfoFileState written = writeNfo(movie, changed);
        movie.applyMetadata(changed);
        movie.markNfoSynchronized(written.modifiedAt());
        events.publishEvent(new MovieFilesChangedEvent(movieId));
        return true;
    }

    /** Die Sprache für das Scrapen dieses Films; {@code null} heißt: die der Einstellungen. */
    public String scrapeLanguage(long movieId) {
        Movie movie = require(movieId);
        return movie.getMetadataLanguage() != null ? movie.getMetadataLanguage()
                : sourceService.languageOf(movie.getSourceId());
    }

    /**
     * Ändert die eigene Sprache des Films in Datenbank und NFO. Eine außerhalb von KMM geänderte NFO wird vorher
     * eingelesen.
     *
     * @param language Sprache oder {@code null}, dann gilt die der Quelle bzw. der Einstellungen
     * @return ob sich etwas geändert hat
     */
    @Transactional
    public boolean changeLanguage(long movieId, String language) {
        Movie movie = require(movieId);
        nfoSync.refreshIfChangedOutside(movie);
        if (!movie.changeMetadataLanguage(language)) {
            return false;
        }
        writeLanguage(movie);
        log.info("Language of movie {} set to {}", movieId, language);
        return true;
    }

    /** Trägt eine eigene Sprache in eine eben erst angelegte NFO ein. */
    @Transactional
    public void storeLanguageInNfo(long movieId) {
        Movie movie = require(movieId);
        if (movie.getMetadataLanguage() != null) {
            writeLanguage(movie);
        }
    }

    private void writeLanguage(Movie movie) {
        nfoFiles.find(movie.companionFiles()).ifPresent(nfo -> {
            try {
                movie.markNfoSynchronized(nfoFiles.writeLanguage(nfo, movie.getMetadataLanguage()).modifiedAt());
            } catch (NfoException e) {
                throw new NfoNotWritableException(e);
            }
        });
    }

    private NfoFileState writeNfo(Movie movie, MovieMetadata metadata) {
        try {
            return nfoFiles.write(movie.companionFiles(), metadata, knownSupplement(movie));
        } catch (NfoException e) {
            throw new NfoNotWritableException(e);
        }
    }

    private MovieNfoSupplement knownSupplement(Movie movie) {
        return movie.isSupplementKnown()
                ? MovieNfoMapper.toNfoSupplement(movie.supplement(), movie.streamDetails(), movie.getAddedAt(),
                        clock.getZone(), movie.nfoTrailer())
                : null;
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }
}
