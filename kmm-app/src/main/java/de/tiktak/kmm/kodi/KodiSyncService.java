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
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.tvshow.EpisodeDetails;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aktualisiert Filme, Serien und Episoden in Kodi: Kennt Kodi den Eintrag, liest es NFO und Bilder neu
 * ein; sonst durchsucht Kodi das Verzeichnis des Films bzw. der Serie. Fehler eines Geräts verhindern den
 * Abgleich der anderen nicht.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KodiSyncService {

    private static final String EPISODE_CODE = "S%02dE%02d";

    private final KodiHostService hostService;
    private final MovieMetadataService metadataService;
    private final TvShowMetadataService tvShowService;
    private final MediaSourceService sourceService;
    private final KodiClient kodiClient;
    private final KodiSyncLog syncLog;
    private final Clock clock;

    /** Aktualisiert den Film auf allen Kodi-Geräten. */
    public List<KodiSyncResult> refreshMovie(long movieId) {
        return refreshMovie(movieId, hostService.targets());
    }

    /** Aktualisiert die Serie (ohne ihre Episoden) auf allen Kodi-Geräten. */
    public List<KodiSyncResult> refreshTvShow(long showId) {
        return refreshTvShow(showId, hostService.targets());
    }

    /** Aktualisiert die Episode auf allen Kodi-Geräten. */
    public List<KodiSyncResult> refreshEpisode(long episodeId) {
        return refreshEpisode(episodeId, hostService.targets());
    }

    /** Lässt alle Kodi-Geräte das Verzeichnis der Quelle nach neuen Filmen bzw. Serien durchsuchen. */
    public List<KodiSyncResult> scanSource(long sourceId) {
        return scanSource(sourceId, hostService.targets());
    }

    void refreshMovieOnRealtimeHosts(long movieId) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            refreshMovie(movieId, targets);
        }
    }

    void refreshTvShowOnRealtimeHosts(long showId) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            refreshTvShow(showId, targets);
        }
    }

    void refreshEpisodeOnRealtimeHosts(long episodeId) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            refreshEpisode(episodeId, targets);
        }
    }

    /**
     * Nach dem Umbenennen eines Films: Kodi liest den neuen Ordner ein und entfernt danach die Einträge aus dem
     * alten, deren Dateien es nicht mehr gibt. Gesehen-Status und Bewertung kommen aus der NFO zurück.
     */
    void syncRenamedMovieOnRealtimeHosts(MovieRenamedEvent event) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            syncAll(targets, target -> syncRenamed(target, event));
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

    private List<KodiSyncResult> refreshTvShow(long showId, List<KodiTarget> targets) {
        TvShowDetails show = tvShowService.findShow(showId);
        return syncAll(targets, target -> refresh(target, show));
    }

    private List<KodiSyncResult> refreshEpisode(long episodeId, List<KodiTarget> targets) {
        EpisodeDetails episode = tvShowService.findEpisode(episodeId);
        TvShowDetails show = tvShowService.findShow(episode.tvShowId());
        return syncAll(targets, target -> refresh(target, show, episode));
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

    private Outcome refresh(KodiTarget target, TvShowDetails show) {
        String title = show.metadata().title();
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(show.directoryPath());
        if (kodiDirectory.isEmpty()) {
            return notMapped(show.directoryPath());
        }
        Optional<Integer> kodiShowId = kodiClient.findTvShow(target.endpoint(), kodiDirectory.get());
        if (kodiShowId.isPresent()) {
            kodiClient.refreshTvShow(target.endpoint(), kodiShowId.get());
            return new Outcome(KodiSyncOutcome.REFRESHED, "„%s“ aktualisiert.".formatted(title));
        }
        return scanUnknown(target, title, kodiDirectory.get());
    }

    /** Eine Episode, die Kodi noch nicht kennt, findet es über einen Scan des Serienordners. */
    private Outcome refresh(KodiTarget target, TvShowDetails show, EpisodeDetails episode) {
        String title = "%s %s".formatted(show.metadata().title(),
                EPISODE_CODE.formatted(episode.season(), episode.episode()));
        Optional<String> kodiFile = target.pathMapper().toKodiPath(episode.filePath());
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(show.directoryPath());
        if (kodiFile.isEmpty() || kodiDirectory.isEmpty()) {
            return notMapped(episode.filePath());
        }
        Optional<Integer> kodiEpisodeId = kodiClient.findEpisode(target.endpoint(), kodiFile.get(), episode.season(),
                episode.episode());
        if (kodiEpisodeId.isPresent()) {
            kodiClient.refreshEpisode(target.endpoint(), kodiEpisodeId.get());
            return new Outcome(KodiSyncOutcome.REFRESHED, "„%s“ aktualisiert.".formatted(title));
        }
        return scanUnknown(target, title, kodiDirectory.get());
    }

    private Outcome scanUnknown(KodiTarget target, String title, String kodiDirectory) {
        kodiClient.scan(target.endpoint(), kodiDirectory);
        return new Outcome(KodiSyncOutcome.SCAN_STARTED,
                "„%s“ ist in Kodi noch unbekannt; Kodi liest %s ein.".formatted(title, kodiDirectory));
    }

    private Outcome syncRenamed(KodiTarget target, MovieRenamedEvent event) {
        Optional<String> directory = target.pathMapper().toKodiDirectory(event.directory());
        Optional<String> previous = target.pathMapper().toKodiDirectory(event.previousDirectory());
        if (directory.isEmpty() || previous.isEmpty()) {
            return notMapped(event.directory());
        }
        kodiClient.scan(target.endpoint(), directory.get());
        kodiClient.cleanMovies(target.endpoint(), previous.get());
        return new Outcome(KodiSyncOutcome.SCAN_STARTED,
                "Kodi liest %s ein und entfernt die alten Einträge aus %s.".formatted(directory.get(), previous.get()));
    }

    private Outcome scan(KodiTarget target, MediaSource source) {
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(source.getPath());
        if (kodiDirectory.isEmpty()) {
            return notMapped(source.getPath());
        }
        kodiClient.scan(target.endpoint(), kodiDirectory.get());
        return new Outcome(KodiSyncOutcome.SCAN_STARTED,
                "Kodi sucht in %s nach neuen %s.".formatted(kodiDirectory.get(),
                        source.getMediaType() == MediaType.TV_SHOW ? "Serien und Episoden" : "Filmen"));
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
