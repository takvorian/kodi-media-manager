package de.tiktak.kmm.tvshow;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
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
 * Scans a TV show source as a background job and takes the result over into the library.
 * Directories and NFOs are read outside the database transaction, several TV shows at the same time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowScanService {

    static final LocalizedText SEARCHING_MESSAGE = LocalizedText.of("tvShow.scan.searching");
    static final LocalizedText SYNCHRONIZING_MESSAGE = LocalizedText.of("tvShow.scan.synchronizing");
    static final int MAX_LISTED_FILES = 100;
    /** TV shows read concurrently; more brings little on network drives and burdens the NAS. */
    static final int PARALLEL_SHOWS = 8;
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final TvShowScannerFactory scannerFactory;
    private final TvShowLibrarySynchronizer synchronizer;
    private final TvShowFolderReader folderReader;
    private final JobService jobService;
    private final ApplicationEventPublisher events;

    /**
     * Checks the source and starts the scan in the background.
     *
     * @throws TvShowProblemException if the source is not a TV show source or is not reachable
     */
    public JobSnapshot startScan(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        Path directory = scannableDirectory(source);
        return jobService.submit(MediaSourceService.SCAN_JOB_KEY_PREFIX + sourceId,
                LocalizedText.of("tvShow.scan.jobTitle").with("source", source.getName()),
                context -> scan(source, directory, context));
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
        events.publishEvent(new TvShowSourceScannedEvent(source.getId(), result.showsAdded() + result.added(),
                result.newWithoutStreams(), result.showsWithNewEpisodes()));
        return result;
    }

    /** Reads the TV shows in parallel; the result keeps the order of the folders. */
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

    /** Reports progress per fully read TV show; the counters never go backwards. */
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
