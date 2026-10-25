package de.tiktak.kmm.movie;

import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.scanner.MovieCandidate;

/**
 * Ein beim Scan gefundener Film samt seiner NFO, falls vorhanden.
 */
record ScannedMovie(MovieCandidate candidate, Optional<NfoFileState> nfo) {

    ScannedMovie {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(nfo, "nfo must not be null");
    }

    String path() {
        return candidate.file().toAbsolutePath().normalize().toString();
    }
}
