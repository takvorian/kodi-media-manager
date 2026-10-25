package de.tiktak.kmm.movie;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.NfoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest und ändert die Metadaten eines Films. Änderungen werden zuerst in die NFO geschrieben;
 * schlägt das fehl, bleibt auch die Datenbank unverändert. Die NFO ist die maßgebliche Quelle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieMetadataService {

    private final MovieRepository movieRepository;
    private final MovieNfoFiles nfoFiles;

    public MovieDetails findDetails(long movieId) {
        Movie movie = require(movieId);
        return MovieDetails.from(movie, nfoFiles.find(movie.companionFiles()));
    }

    @Transactional
    public MovieDetails update(long movieId, MovieMetadata metadata) {
        Movie movie = require(movieId);
        NfoFileState written = writeNfo(movie, metadata);
        movie.applyMetadata(metadata);
        movie.markNfoSynchronized(written.modifiedAt());
        log.info("Metadata of movie {} saved to {}", movieId, written.file());
        return MovieDetails.from(movie, Optional.of(written));
    }

    private NfoFileState writeNfo(Movie movie, MovieMetadata metadata) {
        try {
            return nfoFiles.write(movie.companionFiles(), metadata);
        } catch (NfoException e) {
            throw new NfoNotWritableException(e);
        }
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }
}
