package de.tiktak.kmm.tvshow;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.scanner.TvShowDirectoryScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scans the folder of a single TV show, or one of its seasons, like a source scan does: new episodes are added,
 * vanished ones removed and changed NFOs taken over. A missing folder is only reported; the TV show stays in the
 * library, so that a drive that is briefly unreachable costs no data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowFolderScanService {

    /** Must match {@code TV_SHOW_SCAN_JOB_KEY_PREFIX} in the user interface. */
    static final String JOB_KEY_PREFIX = "tv-scan:";
    private static final int UNKNOWN_TOTAL = 0;

    private final TvShowRepository showRepository;
    private final TvShowScannerFactory scannerFactory;
    private final TvShowFolderReader folderReader;
    private final TvShowLibrarySynchronizer synchronizer;
    private final TvShowBatchService batchService;
    private final JobService jobService;
    private final ApplicationEventPublisher events;

    /**
     * Starts the scan of the TV show or, with a season, of that season in the background.
     *
     * @throws TvShowProblemException if the TV show does not exist
     */
    public JobSnapshot startScan(long showId, OptionalInt season) {
        TvShow show = require(showId);
        LocalizedText title = season.isPresent()
                ? LocalizedText.of("tvShow.folderScan.seasonJobTitle").with("title", show.getTitle())
                        .with("season", season.getAsInt())
                : LocalizedText.of("tvShow.folderScan.jobTitle").with("title", show.getTitle());
        return jobService.submit(JOB_KEY_PREFIX + showId, title, context -> {
            context.reportProgress(0, UNKNOWN_TOTAL, TvShowScanService.SEARCHING_MESSAGE);
            return scan(showId, season);
        });
    }

    /** Scans the TV shows one after another as one job; a TV show that cannot be scanned counts as failed. */
    public JobSnapshot startScans(List<Long> showIds) {
        return batchService.startFill(LocalizedText.of("tvShow.batch.scanJobTitle"), showIds,
                showId -> scan(showId, OptionalInt.empty()).changedSomething());
    }

    /**
     * Reads the folder outside the database transaction and then reconciles the TV show with it.
     *
     * @throws TvShowProblemException if the TV show does not exist, its folder cannot be reached or holds no videos
     */
    TvShowScanResult scan(long showId, OptionalInt season) {
        TvShow show = require(showId);
        Path directory = Path.of(show.getDirectoryPath());
        if (!Files.isDirectory(directory)) {
            throw TvShowProblemException.folderUnreachable(show);
        }
        TvShowDirectoryScanner scanner = scannerFactory.create();
        KnownNfos known = synchronizer.knownNfos(show.getSourceId());
        ScannedTvShow scanned = read(show, scanner, directory, known);
        List<String> unrecognized = scanned.candidate().unrecognizedFiles().stream()
                .limit(TvShowScanService.MAX_LISTED_FILES)
                .map(file -> directory.relativize(file).toString())
                .toList();
        TvShowScanResult result = synchronizer.synchronizeShow(showId, scanned, season, unrecognized);
        log.info("Scanned TV show '{}'{}: episodes {} added, {} updated, {} removed, {} unchanged; {} NFO errors",
                show.getTitle(), season.isPresent() ? " season " + season.getAsInt() : "", result.added(),
                result.updated(), result.removed(), result.unchanged(), result.nfoErrors());
        events.publishEvent(new TvShowFolderScannedEvent(show.getSourceId(), showId, season, result.added(),
                result.removed(), result.newWithoutStreams()));
        return result;
    }

    private ScannedTvShow read(TvShow show, TvShowDirectoryScanner scanner, Path directory, KnownNfos known) {
        try {
            return folderReader.read(scanner, directory, known)
                    .orElseThrow(() -> TvShowProblemException.noVideoFiles(show));
        } catch (UncheckedIOException e) {
            log.warn("Scanning the folder of TV show '{}' failed", show.getTitle(), e);
            throw TvShowProblemException.folderUnreachable(show);
        }
    }

    private TvShow require(long showId) {
        return showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }
}
