package de.tiktak.kmm.movie;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gleicht die Filme einer Quelle in der Datenbank mit dem Scan-Ergebnis ab: Neue Dateien werden
 * angelegt, verschwundene entfernt. Metadaten kommen aus der NFO; bekannte Filme werden nur
 * aktualisiert, wenn sich ihre NFO seit dem letzten Einlesen geändert hat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieLibrarySynchronizer {

    private final MovieRepository movieRepository;
    private final MediaSourceService sourceService;
    private final MovieNfoFiles nfoFiles;
    private final MovieSets movieSets;
    private final Clock clock;

    /** Liefert die Änderungszeiten der zuletzt eingelesenen NFOs je Filmdatei, damit der Scan nur geänderte liest. */
    @Transactional(readOnly = true)
    Map<String, Instant> knownNfos(long sourceId) {
        Map<String, Instant> known = new HashMap<>();
        for (Object[] row : movieRepository.findNfoTimestampsBySourceId(sourceId)) {
            if (row[1] != null) {
                known.put((String) row[0], (Instant) row[1]);
            }
        }
        return known;
    }

    @Transactional
    ScanResult synchronize(long sourceId, List<ScannedMovie> scannedMovies) {
        Map<String, Movie> known = movieRepository.findBySourceId(sourceId).stream()
                .collect(Collectors.toMap(Movie::getFilePath, Function.identity()));
        Set<String> found = scannedMovies.stream().map(ScannedMovie::path).collect(Collectors.toSet());
        Outcome outcome = new Outcome();

        for (ScannedMovie scanned : scannedMovies) {
            Movie movie = known.get(scanned.path());
            if (movie == null) {
                outcome.added.add(newMovie(sourceId, scanned, outcome));
            } else if (nfoChanged(movie, scanned)
                    && applyNfo(movie, scanned, movie.getTitle(), movie.getReleaseYear(), outcome)) {
                outcome.updated++;
            }
        }
        List<Movie> removed = known.values().stream()
                .filter(movie -> !found.contains(movie.getFilePath()))
                .toList();

        movieRepository.saveAll(outcome.added);
        movieRepository.deleteAll(removed);
        sourceService.markScanned(sourceId, clock.instant());
        int unchanged = known.size() - removed.size() - outcome.updated;
        return new ScanResult(sourceId, outcome.added.size(), outcome.updated, removed.size(), unchanged,
                outcome.nfoErrors);
    }

    private Movie newMovie(long sourceId, ScannedMovie scanned, Outcome outcome) {
        MovieCandidate candidate = scanned.candidate();
        Movie movie = new Movie(sourceId, scanned.path(), candidate.title(), candidate.year(), candidate.format(),
                candidate.partCount());
        if (!applyNfo(movie, scanned, candidate.title(), candidate.year(), outcome) && scanned.nfo().isEmpty()) {
            movie.applySupplement(MovieSupplement.EMPTY, null);
        }
        return movie;
    }

    private static boolean nfoChanged(Movie movie, ScannedMovie scanned) {
        return scanned.nfo()
                .map(nfo -> !nfo.modifiedAt().equals(movie.getNfoModifiedAt()))
                .orElse(false);
    }

    /**
     * @return {@code true}, wenn die NFO übernommen wurde
     */
    private boolean applyNfo(Movie movie, ScannedMovie scanned, String fallbackTitle, Integer fallbackYear,
            Outcome outcome) {
        if (scanned.nfo().isEmpty()) {
            return false;
        }
        NfoFileState nfoFile = scanned.nfo().get();
        try {
            MovieNfo nfo = scanned.loaded().isPresent() ? scanned.loaded().get().content() : nfoFiles.read(nfoFile);
            movie.applyMetadata(MovieNfoMapper.toMetadata(nfo, fallbackTitle, fallbackYear));
            applySupplement(movie, nfo.supplement());
            movie.markNfoSynchronized(nfoFile.modifiedAt());
            return true;
        } catch (NfoException e) {
            log.warn("Skipping unreadable NFO: {}", e.getMessage());
            outcome.nfoErrors++;
            return false;
        }
    }

    private void applySupplement(Movie movie, MovieNfoSupplement nfoSupplement) {
        if (nfoSupplement == null) {
            return;
        }
        MovieSupplement supplement = MovieNfoMapper.toSupplement(nfoSupplement, clock.getZone());
        movie.applySupplement(supplement, movieSets.resolve(supplement.set()));
        movie.applyStreamDetails(nfoSupplement.streams());
        Instant dateAdded = MovieNfoMapper.toInstant(nfoSupplement.dateAdded(), clock.getZone());
        if (dateAdded != null) {
            movie.changeAddedAt(dateAdded);
        }
    }

    /** Zwischenstand eines Abgleichs. */
    private static final class Outcome {

        private final List<Movie> added = new ArrayList<>();
        private int updated;
        private int nfoErrors;
    }
}
