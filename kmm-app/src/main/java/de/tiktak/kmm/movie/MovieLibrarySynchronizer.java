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
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.source.ScanDetails;
import de.tiktak.kmm.source.ScanEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciles the movies of a source in the database with the scan result: new files are
 * created, vanished ones removed. Metadata comes from the NFO; known movies are only
 * updated if their NFO has changed since it was last read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieLibrarySynchronizer {

    private final MovieRepository movieRepository;
    private final MediaSourceService sourceService;
    private final MovieNfoFiles nfoFiles;
    private final MovieNfoSync nfoSync;
    private final Clock clock;

    /** Returns the modification times of the last-read NFOs per movie file, so the scan reads only changed ones. */
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
            } else {
                boolean nfoApplied = nfoChanged(movie, scanned)
                        && applyNfo(movie, scanned, movie.getTitle(), movie.getReleaseYear(), outcome);
                boolean filled = fillFromName(movie, scanned);
                boolean retitled = scanned.nfo().isEmpty() && movie.replaceFileTitle(
                        scanned.candidate().fileTitle(), scanned.candidate().title());
                if (nfoApplied || filled || retitled) {
                    outcome.updated++;
                    outcome.details.updated(entry(movie, movie.getId()));
                }
            }
        }
        List<Movie> removed = known.values().stream()
                .filter(movie -> !found.contains(movie.getFilePath()))
                .toList();

        movieRepository.saveAll(outcome.added);
        movieRepository.deleteAll(removed);
        outcome.added.forEach(movie -> outcome.details.added(entry(movie, movie.getId())));
        removed.forEach(movie -> outcome.details.removed(entry(movie, null)));
        sourceService.markScanned(sourceId, clock.instant());
        int unchanged = known.size() - removed.size() - outcome.updated;
        List<Long> withoutStreams = outcome.added.stream()
                .filter(movie -> movie.streamDetails().isEmpty())
                .map(Movie::getId)
                .toList();
        return new ScanResult(sourceId, outcome.added.size(), outcome.updated, removed.size(), unchanged,
                outcome.nfoErrors, outcome.details.build(), withoutStreams);
    }

    private static ScanEntry entry(Movie movie, Long id) {
        boolean yearInTitle = !MovieSearchTitle.of(movie.metadata()).equals(movie.getTitle());
        String year = movie.getReleaseYear() == null || yearInTitle ? "" : " (%d)".formatted(movie.getReleaseYear());
        return new ScanEntry(MediaType.MOVIE, id, movie.getTitle() + year);
    }

    private Movie newMovie(long sourceId, ScannedMovie scanned, Outcome outcome) {
        MovieCandidate candidate = scanned.candidate();
        Movie movie = new Movie(sourceId, scanned.path(), candidate.title(), candidate.year(), candidate.format(),
                candidate.partCount());
        if (!applyNfo(movie, scanned, candidate.title(), candidate.year(), outcome) && scanned.nfo().isEmpty()) {
            movie.applySupplement(MovieSupplement.EMPTY, null);
        }
        fillFromName(movie, scanned);
        return movie;
    }

    private static boolean fillFromName(Movie movie, ScannedMovie scanned) {
        return movie.fillMissingFromName(scanned.candidate().year(), scanned.candidate().imdbId());
    }

    private static boolean nfoChanged(Movie movie, ScannedMovie scanned) {
        return scanned.nfo()
                .map(nfo -> !nfo.modifiedAt().equals(movie.getNfoModifiedAt()))
                .orElse(false);
    }

    /**
     * @return {@code true} if the NFO was taken over
     */
    private boolean applyNfo(Movie movie, ScannedMovie scanned, String fallbackTitle, Integer fallbackYear,
            Outcome outcome) {
        if (scanned.nfo().isEmpty()) {
            return false;
        }
        NfoFileState nfoFile = scanned.nfo().get();
        try {
            MovieNfo nfo = scanned.loaded().isPresent() ? scanned.loaded().get().content() : nfoFiles.read(nfoFile);
            nfoSync.apply(movie, nfoFile, nfo, fallbackTitle, fallbackYear);
            return true;
        } catch (NfoException e) {
            log.warn("Skipping unreadable NFO: {}", e.getMessage());
            outcome.nfoErrors++;
            outcome.details.unreadableNfo(scanned.path());
            return false;
        }
    }

    /** Intermediate state of a reconciliation. */
    private static final class Outcome {

        private final List<Movie> added = new ArrayList<>();
        private int updated;
        private int nfoErrors;
        private final ScanDetails.Builder details = new ScanDetails.Builder();
    }
}
