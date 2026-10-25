package de.tiktak.kmm.movie;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Filmquelle als Hintergrund-Job und übernimmt das Ergebnis in die Bibliothek.
 * Verzeichnisse und NFO-Zeitstempel werden außerhalb der Datenbanktransaktion gelesen, damit lange
 * Scans keine Transaktion offen halten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieScanService {

    /** Präfix des Job-Schlüssels; pro Quelle läuft höchstens ein Scan. */
    public static final String JOB_KEY_PREFIX = "movie-scan:";
    static final String SEARCHING_MESSAGE = "Verzeichnisse werden durchsucht";
    static final String CHECKING_NFOS_MESSAGE = "NFOs werden geprüft";
    static final String SYNCHRONIZING_MESSAGE = "Bibliothek wird abgeglichen";
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
        List<MovieCandidate> candidates = findCandidates(source, directory);
        List<ScannedMovie> scannedMovies = withNfos(candidates, context);
        context.throwIfCancellationRequested();
        context.reportProgress(candidates.size(), candidates.size(), SYNCHRONIZING_MESSAGE);
        ScanResult result = synchronizer.synchronize(source.getId(), scannedMovies);
        log.info("Scanned source '{}': {} added, {} updated, {} removed, {} unchanged, {} NFO errors",
                source.getName(), result.added(), result.updated(), result.removed(), result.unchanged(),
                result.nfoErrors());
        events.publishEvent(new MovieSourceScannedEvent(source.getId(), result.added(), result.removed()));
        return result;
    }

    private List<ScannedMovie> withNfos(List<MovieCandidate> candidates, JobContext context) {
        List<ScannedMovie> scannedMovies = new ArrayList<>(candidates.size());
        for (MovieCandidate candidate : candidates) {
            context.throwIfCancellationRequested();
            CompanionFiles companionFiles = CompanionFiles.of(candidate.file(), candidate.format(),
                    candidate.partCount());
            scannedMovies.add(new ScannedMovie(candidate, nfoFiles.find(companionFiles)));
            context.reportProgress(scannedMovies.size(), candidates.size(), CHECKING_NFOS_MESSAGE);
        }
        return scannedMovies;
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

    private List<MovieCandidate> findCandidates(MediaSource source, Path directory) {
        try {
            return scannerFactory.create().scan(directory);
        } catch (UncheckedIOException e) {
            log.warn("Scanning source '{}' failed", source.getName(), e);
            throw SourceNotScannableException.unreachable(source);
        }
    }
}
