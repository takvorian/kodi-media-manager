package de.tiktak.kmm.movie;

import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.scanner.MovieCandidate;

/**
 * Ein beim Scan gefundener Film samt seiner NFO, falls vorhanden.
 *
 * @param loaded der vorab gelesene Inhalt der NFO; leer, wenn sie seit dem letzten Scan unverändert ist
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
