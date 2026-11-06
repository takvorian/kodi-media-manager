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
 * Reads and changes the metadata of a movie. Changes are written to the NFO first;
 * if that fails, the database stays unchanged as well. The NFO is the authoritative source.
 * After saving, a {@link MovieFilesChangedEvent} is published.
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

    /** The movie for the movie view; an NFO changed outside KMM is read in first. */
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
     * Changes the metadata starting from the current state, e.g. in batch edit. An NFO changed outside KMM
     * is read in first so that the change builds on it.
     *
     * @return whether anything changed
     * @throws NfoNotWritableException if the NFO cannot be written
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

    /** The language for scraping this movie; {@code null} means: the one from the settings. */
    public String scrapeLanguage(long movieId) {
        Movie movie = require(movieId);
        return movie.getMetadataLanguage() != null ? movie.getMetadataLanguage()
                : sourceService.languageOf(movie.getSourceId());
    }

    /**
     * Changes the movie's own language in the database and NFO. An NFO changed outside KMM is
     * read in first.
     *
     * @param language language or {@code null}, in which case the source or settings language applies
     * @return whether anything changed
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

    /** Enters an own language into a freshly created NFO. */
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
