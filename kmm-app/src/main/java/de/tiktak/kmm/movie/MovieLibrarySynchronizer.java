package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
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
 * updated if their NFO has changed since it was last read. A new file that uses the NFO of a
 * vanished one replaces it in place (e.g. an AVI converted to MKV), so the entry is kept.
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
    private final ApplicationEventPublisher events;

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
        ScanResult result = reconcile(sourceId, movieRepository.findBySourceId(sourceId), scannedMovies);
        sourceService.markScanned(sourceId, clock.instant());
        return result;
    }

    /**
     * Like a source scan, but only for the movies in one folder; the source does not count as scanned.
     *
     * @param inFolder tells by the movie folder whether a known movie belongs to the scanned folder
     */
    @Transactional
    ScanResult synchronizeFolder(long sourceId, Predicate<Path> inFolder, List<ScannedMovie> scannedMovies) {
        List<Movie> known = movieRepository.findBySourceId(sourceId).stream()
                .filter(movie -> inFolder.test(movie.companionFiles().movieDirectory()))
                .toList();
        return reconcile(sourceId, known, scannedMovies);
    }

    private ScanResult reconcile(long sourceId, List<Movie> knownMovies, List<ScannedMovie> scannedMovies) {
        Map<String, Movie> known = knownMovies.stream()
                .collect(Collectors.toMap(Movie::getFilePath, Function.identity()));
        Set<String> found = scannedMovies.stream().map(ScannedMovie::path).collect(Collectors.toSet());
        Outcome outcome = new Outcome();
        List<ScannedMovie> newFiles = new ArrayList<>();

        for (ScannedMovie scanned : scannedMovies) {
            Movie movie = known.get(scanned.path());
            if (movie == null) {
                newFiles.add(scanned);
            } else if (updateKnown(movie, scanned, outcome)) {
                outcome.updated++;
                outcome.details.updated(entry(movie, movie.getId()));
            }
        }
        List<Movie> removed = known.values().stream()
                .filter(movie -> !found.contains(movie.getFilePath()))
                .collect(Collectors.toCollection(ArrayList::new));
        for (ScannedMovie scanned : newFiles) {
            Optional<Movie> replaced = takeReplacedBy(scanned, removed);
            if (replaced.isPresent()) {
                replaceFile(replaced.get(), scanned, outcome);
            } else {
                outcome.added.add(newMovie(sourceId, scanned, outcome));
            }
        }

        movieRepository.saveAll(outcome.added);
        movieRepository.deleteAll(removed);
        outcome.added.forEach(movie -> outcome.details.added(entry(movie, movie.getId())));
        removed.forEach(movie -> outcome.details.removed(entry(movie, null)));
        int unchanged = known.size() - removed.size() - outcome.updated;
        List<Long> toProbe = new ArrayList<>(outcome.replaced);
        outcome.added.stream()
                .filter(movie -> movie.streamDetails().isEmpty())
                .map(Movie::getId)
                .forEach(toProbe::add);
        return new ScanResult(sourceId, outcome.added.size(), outcome.updated, removed.size(), unchanged,
                outcome.nfoErrors, outcome.details.build(), toProbe);
    }

    /**
     * @return {@code true} if something about the known movie has changed
     */
    private boolean updateKnown(Movie movie, ScannedMovie scanned, Outcome outcome) {
        boolean nfoApplied = nfoChanged(movie, scanned)
                && applyNfo(movie, scanned, movie.getTitle(), movie.getReleaseYear(), outcome);
        boolean filled = fillFromName(movie, scanned);
        boolean retitled = scanned.nfo().isEmpty() && movie.replaceFileTitle(
                scanned.candidate().fileTitle(), scanned.candidate().title());
        return nfoApplied || filled || retitled;
    }

    /**
     * Finds the vanished movie whose NFO the new file uses and takes it out of the removed ones. Without an NFO
     * nothing ties the new file to an old entry.
     */
    private static Optional<Movie> takeReplacedBy(ScannedMovie scanned, List<Movie> removed) {
        if (scanned.nfo().isEmpty()) {
            return Optional.empty();
        }
        Path nfo = normalized(scanned.nfo().get().file());
        for (Iterator<Movie> it = removed.iterator(); it.hasNext(); ) {
            Movie movie = it.next();
            if (movie.companionFiles().nfoCandidates().stream().map(MovieLibrarySynchronizer::normalized)
                    .anyMatch(nfo::equals)) {
                it.remove();
                return Optional.of(movie);
            }
        }
        return Optional.empty();
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /** Keeps the entry with its metadata; the technical details belong to the old file and are read again. */
    private void replaceFile(Movie movie, ScannedMovie scanned, Outcome outcome) {
        String previousFilePath = movie.getFilePath();
        MovieCandidate candidate = scanned.candidate();
        movie.replaceFile(scanned.path(), candidate.format(), candidate.partCount());
        if (nfoChanged(movie, scanned)) {
            applyNfo(movie, scanned, movie.getTitle(), movie.getReleaseYear(), outcome);
        }
        fillFromName(movie, scanned);
        outcome.updated++;
        outcome.details.updated(entry(movie, movie.getId()));
        outcome.replaced.add(movie.getId());
        events.publishEvent(new MovieFileReplacedEvent(movie.getId(), movie.getSourceId(), previousFilePath));
        log.info("Movie {}: video file {} replaced by {}", movie.getId(), previousFilePath, movie.getFilePath());
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
        private final List<Long> replaced = new ArrayList<>();
        private int updated;
        private int nfoErrors;
        private final ScanDetails.Builder details = new ScanDetails.Builder();
    }
}
