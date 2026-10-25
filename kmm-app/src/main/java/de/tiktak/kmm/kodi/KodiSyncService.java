package de.tiktak.kmm.kodi;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aktualisiert Filme in Kodi: Kennt Kodi den Film, liest es NFO und Bilder neu ein; sonst durchsucht
 * Kodi das Verzeichnis des Films. Fehler eines Geräts verhindern den Abgleich der anderen nicht.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KodiSyncService {

    private final KodiHostService hostService;
    private final MovieMetadataService metadataService;
    private final MediaSourceService sourceService;
    private final KodiClient kodiClient;
    private final KodiSyncLog syncLog;
    private final Clock clock;

    /** Aktualisiert den Film auf allen Kodi-Geräten. */
    public List<KodiSyncResult> refreshMovie(long movieId) {
        return refreshMovie(movieId, hostService.targets());
    }

    /** Lässt alle Kodi-Geräte das Verzeichnis der Quelle nach neuen Filmen durchsuchen. */
    public List<KodiSyncResult> scanSource(long sourceId) {
        return scanSource(sourceId, hostService.targets());
    }

    void refreshMovieOnRealtimeHosts(long movieId) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            refreshMovie(movieId, targets);
        }
    }

    void scanSourceOnRealtimeHosts(long sourceId) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            scanSource(sourceId, targets);
        }
    }

    private List<KodiSyncResult> refreshMovie(long movieId, List<KodiTarget> targets) {
        MovieDetails movie = metadataService.findDetails(movieId);
        return syncAll(targets, target -> refresh(target, movie));
    }

    private List<KodiSyncResult> scanSource(long sourceId, List<KodiTarget> targets) {
        MediaSource source = sourceService.require(sourceId);
        return syncAll(targets, target -> scan(target, source));
    }

    private Outcome refresh(KodiTarget target, MovieDetails movie) {
        String title = movie.metadata().title();
        Optional<String> kodiFile = target.pathMapper().toKodiPath(movie.filePath());
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(movieDirectory(movie));
        if (kodiFile.isEmpty() || kodiDirectory.isEmpty()) {
            return notMapped(movie.filePath());
        }
        Optional<Integer> kodiMovieId = kodiClient.findMovie(target.endpoint(), kodiFile.get());
        if (kodiMovieId.isPresent()) {
            kodiClient.refreshMovie(target.endpoint(), kodiMovieId.get());
            return new Outcome(KodiSyncOutcome.REFRESHED, "„%s“ aktualisiert.".formatted(title));
        }
        kodiClient.scan(target.endpoint(), kodiDirectory.get());
        return new Outcome(KodiSyncOutcome.SCAN_STARTED,
                "„%s“ ist in Kodi noch unbekannt; Kodi liest %s ein.".formatted(title, kodiDirectory.get()));
    }

    private Outcome scan(KodiTarget target, MediaSource source) {
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(source.getPath());
        if (kodiDirectory.isEmpty()) {
            return notMapped(source.getPath());
        }
        kodiClient.scan(target.endpoint(), kodiDirectory.get());
        return new Outcome(KodiSyncOutcome.SCAN_STARTED,
                "Kodi sucht in %s nach neuen Filmen.".formatted(kodiDirectory.get()));
    }

    private List<KodiSyncResult> syncAll(List<KodiTarget> targets, Function<KodiTarget, Outcome> sync) {
        return targets.stream().map(target -> {
            Outcome outcome = attempt(target, sync);
            KodiSyncResult result = new KodiSyncResult(target.id(), target.name(), outcome.outcome(),
                    outcome.message(), clock.instant());
            syncLog.record(result);
            log(result);
            return result;
        }).toList();
    }

    private static Outcome attempt(KodiTarget target, Function<KodiTarget, Outcome> sync) {
        try {
            return sync.apply(target);
        } catch (KodiException e) {
            return new Outcome(KodiSyncOutcome.FAILED, e.getMessage());
        }
    }

    /** Bei DVD- und Blu-ray-Ordnern liegt die Hauptdatei eine Ebene unter dem Filmordner. */
    private static String movieDirectory(MovieDetails movie) {
        Path directory = Path.of(movie.filePath()).getParent();
        return movie.discFormat() == DiscFormat.FILE ? directory.toString() : directory.getParent().toString();
    }

    private static Outcome notMapped(String path) {
        return new Outcome(KodiSyncOutcome.NOT_MAPPED, "Keine Pfad-Zuordnung für %s.".formatted(path));
    }

    private List<KodiTarget> realtimeTargets() {
        return hostService.targets().stream().filter(KodiTarget::realtimeSync).toList();
    }

    private static void log(KodiSyncResult result) {
        if (result.failed()) {
            log.warn("Kodi sync with '{}' failed: {}", result.host(), result.message());
        } else {
            log.info("Kodi sync with '{}': {}", result.host(), result.message());
        }
    }

    private record Outcome(KodiSyncOutcome outcome, String message) {
    }
}
