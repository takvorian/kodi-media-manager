package de.tiktak.kmm.movie;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scans the folder of a single movie like a source scan does: a replaced video file is taken over, new movies in the
 * folder are added, vanished ones removed and changed NFOs taken over. A missing or empty folder is only reported.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieFolderScanService {

    /** Must match {@code MOVIE_SCAN_JOB_KEY_PREFIX} in the user interface. */
    static final String JOB_KEY_PREFIX = "movie-scan:";
    private static final int UNKNOWN_TOTAL = 0;

    private final MovieRepository movieRepository;
    private final MediaSourceService sourceService;
    private final MovieScannerFactory scannerFactory;
    private final MovieNfoFiles nfoFiles;
    private final MovieLibrarySynchronizer synchronizer;
    private final MovieBatchService batchService;
    private final JobService jobService;
    private final ApplicationEventPublisher events;

    /**
     * Starts the scan of the movie's folder in the background.
     *
     * @throws MovieNotFoundException if the movie does not exist
     */
    public JobSnapshot startScan(long movieId) {
        Movie movie = require(movieId);
        return jobService.submit(JOB_KEY_PREFIX + movieId,
                LocalizedText.of("movie.folderScan.jobTitle").with("title", movie.getTitle()), context -> {
                    context.reportProgress(0, UNKNOWN_TOTAL, MovieScanService.SEARCHING_MESSAGE);
                    return scan(movieId);
                });
    }

    /** Scans the folders of the movies one after another as one job; a movie that cannot be scanned counts as failed. */
    public JobSnapshot startScans(List<Long> movieIds) {
        return batchService.startFill(LocalizedText.of("movie.batch.scan.jobTitle"), movieIds,
                movieId -> changedSomething(scan(movieId)));
    }

    /**
     * Reads the folder outside the database transaction and then reconciles its movies with it.
     *
     * @throws MovieNotFoundException   if the movie does not exist
     * @throws MovieFolderScanException if the folder cannot be reached or holds no videos
     */
    ScanResult scan(long movieId) {
        Movie movie = require(movieId);
        Path root = normalized(Path.of(sourceService.require(movie.getSourceId()).getPath()));
        Path directory = normalized(movie.companionFiles().movieDirectory());
        if (!Files.isDirectory(directory)) {
            throw MovieFolderScanException.unreachable(movie, directory);
        }
        List<MovieCandidate> candidates = read(movie, root, directory);
        Map<String, Instant> knownNfos = synchronizer.knownNfos(movie.getSourceId());
        List<ScannedMovie> scanned = candidates.stream()
                .map(candidate -> MovieScanService.withNfo(nfoFiles, candidate, knownNfos))
                .toList();
        Predicate<Path> inFolder = directory.equals(root)
                ? folder -> normalized(folder).equals(root)
                : folder -> normalized(folder).startsWith(directory);
        ScanResult result = synchronizer.synchronizeFolder(movie.getSourceId(), inFolder, scanned);
        log.info("Scanned folder {}: {} added, {} updated, {} removed, {} unchanged, {} NFO errors", directory,
                result.added(), result.updated(), result.removed(), result.unchanged(), result.nfoErrors());
        events.publishEvent(new MovieFolderScannedEvent(movie.getSourceId(), directory.toString(), result.added(),
                result.removed(), result.newWithoutStreams()));
        return result;
    }

    private List<MovieCandidate> read(Movie movie, Path root, Path directory) {
        List<MovieCandidate> candidates;
        try {
            candidates = scannerFactory.create().scanFolder(root, directory);
        } catch (UncheckedIOException e) {
            log.warn("Scanning the folder {} failed", directory, e);
            throw MovieFolderScanException.unreachable(movie, directory);
        }
        if (candidates.isEmpty()) {
            throw MovieFolderScanException.noVideoFiles(movie, directory);
        }
        return candidates;
    }

    private static boolean changedSomething(ScanResult result) {
        return result.added() + result.updated() + result.removed() > 0;
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }
}
