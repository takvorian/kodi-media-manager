package de.tiktak.kmm.tvshow;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.EpisodeCandidate;
import de.tiktak.kmm.scanner.TvShowCandidate;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Serienquelle als Hintergrund-Job und übernimmt das Ergebnis in die Bibliothek.
 * Verzeichnisse und NFO-Zeitstempel werden außerhalb der Datenbanktransaktion gelesen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowScanService {

    static final String SEARCHING_MESSAGE = "Verzeichnisse werden durchsucht";
    static final String SYNCHRONIZING_MESSAGE = "Bibliothek wird abgeglichen";
    static final int MAX_LISTED_FILES = 100;
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final TvShowScannerFactory scannerFactory;
    private final TvShowLibrarySynchronizer synchronizer;
    private final TvShowNfoFiles nfoFiles;
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
        List<TvShowCandidate> candidates = findCandidates(source, directory);
        List<ScannedTvShow> scanned = new ArrayList<>(candidates.size());
        List<String> unrecognized = new ArrayList<>();
        for (TvShowCandidate candidate : candidates) {
            context.throwIfCancellationRequested();
            context.reportProgress(scanned.size(), candidates.size(), candidate.title());
            scanned.add(withNfos(candidate));
            candidate.unrecognizedFiles().stream()
                    .limit(Math.max(0, MAX_LISTED_FILES - unrecognized.size()))
                    .forEach(file -> unrecognized.add(directory.relativize(file).toString()));
        }
        context.throwIfCancellationRequested();
        context.reportProgress(candidates.size(), candidates.size(), SYNCHRONIZING_MESSAGE);
        TvShowScanResult result = synchronizer.synchronize(source.getId(), scanned, unrecognized);
        log.info("Scanned TV source '{}': {} shows added, {} removed; episodes {} added, {} updated, {} removed, "
                        + "{} unchanged; {} NFO errors", source.getName(), result.showsAdded(), result.showsRemoved(),
                result.added(), result.updated(), result.removed(), result.unchanged(), result.nfoErrors());
        events.publishEvent(new TvShowSourceScannedEvent(source.getId(), result.showsAdded() + result.added()));
        return result;
    }

    private ScannedTvShow withNfos(TvShowCandidate candidate) {
        Map<Path, TvNfoState> episodeNfos = new HashMap<>();
        for (EpisodeCandidate episode : candidate.episodes()) {
            nfoFiles.findEpisodeNfo(episode.file()).ifPresent(nfo -> episodeNfos.put(episode.file(), nfo));
        }
        return new ScannedTvShow(candidate, nfoFiles.findShowNfo(candidate.directory()), episodeNfos);
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

    private List<TvShowCandidate> findCandidates(MediaSource source, Path directory) {
        try {
            return scannerFactory.create().scan(directory);
        } catch (UncheckedIOException e) {
            log.warn("Scanning TV source '{}' failed", source.getName(), e);
            throw TvShowProblemException.unreachable(source);
        }
    }
}
