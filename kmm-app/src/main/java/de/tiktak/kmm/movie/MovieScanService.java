package de.tiktak.kmm.movie;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.ParallelReads;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Filmquelle als Hintergrund-Job und übernimmt das Ergebnis in die Bibliothek.
 * Verzeichnisse und NFOs werden außerhalb der Datenbanktransaktion und parallel gelesen, damit lange
 * Scans keine Transaktion offen halten; unveränderte NFOs liest der Scan nicht erneut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieScanService {

    /** Präfix des Job-Schlüssels; pro Quelle läuft höchstens ein Scan. */
    public static final String JOB_KEY_PREFIX = MediaSourceService.SCAN_JOB_KEY_PREFIX;
    static final String SEARCHING_MESSAGE = "Verzeichnisse werden durchsucht";
    static final String CHECKING_NFOS_MESSAGE = "NFOs werden geprüft";
    static final String SYNCHRONIZING_MESSAGE = "Bibliothek wird abgeglichen";
    /** Gleichzeitig gelesene Ordner bzw. NFOs; mehr bringt auf Netzlaufwerken kaum etwas und belastet das NAS. */
    static final int PARALLEL_READS = 8;
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final MovieScannerFactory scannerFactory;
    private final MovieLibrarySynchronizer synchronizer;
    private final MovieNfoFiles nfoFiles;
    private final JobService jobService;
    private final ApplicationEventPublisher events;

    /**
     * Prüft die Quelle und startet den Scan im Hintergrund.
     *
     * @throws SourceNotScannableException wenn die Quelle keine Filmquelle oder nicht erreichbar ist
     */
    public JobSnapshot startScan(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        Path directory = scannableDirectory(source);
        return jobService.submit(JOB_KEY_PREFIX + sourceId, "Scan „%s“".formatted(source.getName()),
                context -> scan(source, directory, context));
    }

    ScanResult scan(MediaSource source, Path directory, JobContext context) {
        context.reportProgress(0, UNKNOWN_TOTAL, SEARCHING_MESSAGE);
        List<MovieCandidate> candidates = findCandidates(source, directory, context);
        Map<String, Instant> knownNfos = synchronizer.knownNfos(source.getId());
        List<ScannedMovie> scannedMovies = withNfos(candidates, knownNfos, context);
        context.throwIfCancellationRequested();
        context.reportProgress(candidates.size(), candidates.size(), SYNCHRONIZING_MESSAGE);
        ScanResult result = synchronizer.synchronize(source.getId(), scannedMovies);
        log.info("Scanned source '{}': {} added, {} updated, {} removed, {} unchanged, {} NFO errors",
                source.getName(), result.added(), result.updated(), result.removed(), result.unchanged(),
                result.nfoErrors());
        events.publishEvent(new MovieSourceScannedEvent(source.getId(), result.added(), result.removed()));
        return result;
    }

    /** Sucht die NFOs parallel und liest neue oder geänderte gleich mit; die Reihenfolge bleibt erhalten. */
    private List<ScannedMovie> withNfos(List<MovieCandidate> candidates, Map<String, Instant> knownNfos,
                                        JobContext context) {
        Progress progress = new Progress(context, candidates.size(), CHECKING_NFOS_MESSAGE);
        return ParallelReads.map(candidates, PARALLEL_READS, "movie-nfo-", candidate -> {
            context.throwIfCancellationRequested();
            ScannedMovie scanned = withNfo(candidate, knownNfos);
            progress.done();
            return scanned;
        });
    }

    private ScannedMovie withNfo(MovieCandidate candidate, Map<String, Instant> knownNfos) {
        CompanionFiles companionFiles = CompanionFiles.of(candidate.file(), candidate.format(), candidate.partCount());
        Optional<NfoFileState> nfo = nfoFiles.find(companionFiles);
        String path = new ScannedMovie(candidate, nfo).path();
        Optional<LoadedMovieNfo> loaded = nfo
                .filter(state -> !state.modifiedAt().equals(knownNfos.get(path)))
                .map(state -> LoadedMovieNfo.read(() -> nfoFiles.read(state)));
        return new ScannedMovie(candidate, nfo, loaded);
    }

    private static Path scannableDirectory(MediaSource source) {
        if (source.getMediaType() != MediaType.MOVIE) {
            throw SourceNotScannableException.notAMovieSource(source);
        }
        Path directory = Path.of(source.getPath());
        if (!Files.isDirectory(directory)) {
            throw SourceNotScannableException.unreachable(source);
        }
        return directory;
    }

    /** Durchsucht die Unterordner der Quelle parallel. */
    private List<MovieCandidate> findCandidates(MediaSource source, Path directory, JobContext context) {
        try {
            return scannerFactory.create().scan(directory, (directories, scan) -> {
                Progress progress = new Progress(context, directories.size(), SEARCHING_MESSAGE);
                return ParallelReads.map(directories, PARALLEL_READS, "movie-scan-", subdirectory -> {
                    context.throwIfCancellationRequested();
                    List<MovieCandidate> found = scan.apply(subdirectory);
                    progress.done();
                    return found;
                });
            });
        } catch (UncheckedIOException e) {
            log.warn("Scanning source '{}' failed", source.getName(), e);
            throw SourceNotScannableException.unreachable(source);
        }
    }

    /** Meldet den Fortschritt paralleler Schritte; die Zähler steigen nie rückwärts. */
    private static final class Progress {

        private final JobContext context;
        private final int total;
        private final String message;
        private int done;

        private Progress(JobContext context, int total, String message) {
            this.context = context;
            this.total = total;
            this.message = message;
        }

        synchronized void done() {
            done++;
            context.reportProgress(done, total, message);
        }
    }
}
