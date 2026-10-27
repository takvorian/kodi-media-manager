package de.tiktak.kmm.tvshow;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.ParallelReads;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.TvShowDirectoryScanner;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Serienquelle als Hintergrund-Job und übernimmt das Ergebnis in die Bibliothek.
 * Verzeichnisse und NFOs werden außerhalb der Datenbanktransaktion gelesen, mehrere Serien gleichzeitig.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowScanService {

    static final String SEARCHING_MESSAGE = "Verzeichnisse werden durchsucht";
    static final String SYNCHRONIZING_MESSAGE = "Bibliothek wird abgeglichen";
    static final int MAX_LISTED_FILES = 100;
    /** Gleichzeitig eingelesene Serien; mehr bringt auf Netzlaufwerken kaum etwas und belastet das NAS. */
    static final int PARALLEL_SHOWS = 8;
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final TvShowScannerFactory scannerFactory;
    private final TvShowLibrarySynchronizer synchronizer;
    private final TvShowFolderReader folderReader;
    private final JobService jobService;
    private final ApplicationEventPublisher events;

    /**
     * Prüft die Quelle und startet den Scan im Hintergrund.
     *
     * @throws TvShowProblemException wenn die Quelle keine Serienquelle oder nicht erreichbar ist
     */
    public JobSnapshot startScan(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        Path directory = scannableDirectory(source);
        return jobService.submit(MediaSourceService.SCAN_JOB_KEY_PREFIX + sourceId,
                "Scan „%s“".formatted(source.getName()), context -> scan(source, directory, context));
    }

    TvShowScanResult scan(MediaSource source, Path directory, JobContext context) {
        context.reportProgress(0, UNKNOWN_TOTAL, SEARCHING_MESSAGE);
        TvShowDirectoryScanner scanner = scannerFactory.create();
        List<Path> showDirectories = findShowDirectories(source, scanner, directory);
        KnownNfos known = synchronizer.knownNfos(source.getId());
        List<ScannedTvShow> scanned = readShows(scanner, showDirectories, known, context);
        context.throwIfCancellationRequested();
        context.reportProgress(showDirectories.size(), showDirectories.size(), SYNCHRONIZING_MESSAGE);
        List<String> unrecognized = scanned.stream()
                .flatMap(show -> show.candidate().unrecognizedFiles().stream())
                .limit(MAX_LISTED_FILES)
                .map(file -> directory.relativize(file).toString())
                .toList();
        TvShowScanResult result = synchronizer.synchronize(source.getId(), scanned, unrecognized);
        log.info("Scanned TV source '{}': {} shows added, {} removed; episodes {} added, {} updated, {} removed, "
                        + "{} unchanged; {} NFO errors", source.getName(), result.showsAdded(), result.showsRemoved(),
                result.added(), result.updated(), result.removed(), result.unchanged(), result.nfoErrors());
        events.publishEvent(new TvShowSourceScannedEvent(source.getId(), result.showsAdded() + result.added()));
        return result;
    }

    /** Liest die Serien parallel ein; das Ergebnis behält die Reihenfolge der Ordner. */
    private List<ScannedTvShow> readShows(TvShowDirectoryScanner scanner, List<Path> showDirectories,
                                          KnownNfos known, JobContext context) {
        ReadProgress progress = new ReadProgress(context, showDirectories.size());
        return ParallelReads.map(showDirectories, PARALLEL_SHOWS, "tv-scan-", showDirectory -> {
            if (context.isCancellationRequested()) {
                return Optional.<ScannedTvShow>empty();
            }
            Optional<ScannedTvShow> show = folderReader.read(scanner, showDirectory, known);
            progress.showRead(showDirectory);
            return show;
        }).stream().flatMap(Optional::stream).toList();
    }

    private static Path scannableDirectory(MediaSource source) {
        if (source.getMediaType() != MediaType.TV_SHOW) {
            throw TvShowProblemException.notATvSource(source);
        }
        Path directory = Path.of(source.getPath());
        if (!Files.isDirectory(directory)) {
            throw TvShowProblemException.unreachable(source);
        }
        return directory;
    }

    private List<Path> findShowDirectories(MediaSource source, TvShowDirectoryScanner scanner, Path directory) {
        try {
            return scanner.showDirectories(directory);
        } catch (UncheckedIOException e) {
            log.warn("Scanning TV source '{}' failed", source.getName(), e);
            throw TvShowProblemException.unreachable(source);
        }
    }

    /** Meldet den Fortschritt je fertig eingelesener Serie; die Zähler steigen nie rückwärts. */
    private static final class ReadProgress {

        private final JobContext context;
        private final int total;
        private int done;

        private ReadProgress(JobContext context, int total) {
            this.context = context;
            this.total = total;
        }

        synchronized void showRead(Path showDirectory) {
            done++;
            context.reportProgress(done, total, showDirectory.getFileName().toString());
        }
    }
}
