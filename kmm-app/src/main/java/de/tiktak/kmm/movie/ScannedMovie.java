package de.tiktak.kmm.movie;

import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.scanner.MovieCandidate;

/**
 * A movie found during the scan, together with its NFO if present.
 *
 * @param loaded the pre-read content of the NFO; empty if it is unchanged since the last scan
 */
record ScannedMovie(MovieCandidate candidate, Optional<NfoFileState> nfo, Optional<LoadedMovieNfo> loaded) {

    ScannedMovie {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(nfo, "nfo must not be null");
        Objects.requireNonNull(loaded, "loaded must not be null");
    }

    ScannedMovie(MovieCandidate candidate, Optional<NfoFileState> nfo) {
        this(candidate, nfo, Optional.empty());
    }

    String path() {
        return candidate.file().toAbsolutePath().normalize().toString();
    }
}
