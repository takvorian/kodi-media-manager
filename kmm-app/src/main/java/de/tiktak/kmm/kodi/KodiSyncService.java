package de.tiktak.kmm.kodi;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.artwork.MovieSetArtworkService;
import de.tiktak.kmm.artwork.MovieSetImage;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.kodi.rpc.KodiLibraryScheduler;
import de.tiktak.kmm.kodi.rpc.KodiSources;
import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSetDetails;
import de.tiktak.kmm.movie.MovieSetProblemException;
import de.tiktak.kmm.movie.MovieSetService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.tvshow.EpisodeDetails;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowRenamedEvent;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Updates movies, TV shows and episodes in Kodi: if Kodi knows the entry, it reads NFO and images again; otherwise
 * Kodi scans the directory of the movie or TV show. Errors of one device do not prevent the sync of the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KodiSyncService {

    private static final String EPISODE_CODE = "S%02dE%02d";

    private final KodiHostService hostService;
    private final MovieMetadataService metadataService;
    private final MovieSetService movieSetService;
    private final MovieSetArtworkService movieSetArtwork;
    private final TvShowMetadataService tvShowService;
    private final MediaSourceService sourceService;
    private final KodiClient kodiClient;
    private final KodiLibraryScheduler scheduler;
    private final KodiProperties properties;
    private final KodiSyncLog syncLog;
    private final Clock clock;

    /** Updates the movie on all Kodi devices. */
    public List<KodiSyncResult> refreshMovie(long movieId) {
        return refreshMovie(movieId, hostService.targets(), properties.scanWaitInteractive());
    }

    /** Updates the TV show (without its episodes) on all Kodi devices. */
    public List<KodiSyncResult> refreshTvShow(long showId) {
        return refreshTvShow(showId, hostService.targets(), properties.scanWaitInteractive());
    }

    /** Updates the episode on all Kodi devices. */
    public List<KodiSyncResult> refreshEpisode(long episodeId) {
        return refreshEpisode(episodeId, hostService.targets(), properties.scanWaitInteractive());
    }

    /** Lets all Kodi devices scan the directory of the source for new movies or TV shows. */
    public List<KodiSyncResult> scanSource(long sourceId) {
        return scanSource(sourceId, hostService.targets(), properties.scanWaitInteractive());
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
            refreshEpisode(episodeId, targets, properties.scanWaitBackground());
        }
    }

    /**
     * After renaming a movie: Kodi scans the new folder and then removes the entries from the old one whose files no
     * longer exist. Watched status and rating come back from the NFO.
     */
    void syncRenamedMovieOnRealtimeHosts(MovieRenamedEvent event) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            syncAll(targets, target -> syncRenamed(target, event.directory(), event.previousDirectory(),
                    scheduler::cleanMovies));
        }
    }

    /** After renaming a TV show as for movies: scan the new folder, clean the old one. */
    void syncRenamedTvShowOnRealtimeHosts(TvShowRenamedEvent event) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            syncAll(targets, target -> syncRenamed(target, event.directory(), event.previousDirectory(),
                    scheduler::cleanTvShows));
        }
    }

    /**
     * Takes over title and overview of a movie set into Kodi, like Ember. Kodi finds the set by its previous title or
     * by one of its movies; if Kodi does not know it yet, it arrives when the movies are scanned.
     */
    void syncMovieSetOnRealtimeHosts(MovieSetChangedEvent event) {
        List<KodiTarget> targets = realtimeTargets();
        if (targets.isEmpty()) {
            return;
        }
        MovieSetDetails set;
        try {
            set = movieSetService.find(event.setId());
        } catch (MovieSetProblemException e) {
            log.debug("Movie set {} is gone, nothing to sync", event.setId());
            return;
        }
        Optional<String> memberFile = set.movies().stream().findFirst()
                .map(member -> metadataService.findDetails(member.id()).filePath());
        Optional<List<MovieSetImage>> images = movieSetArtwork.imagesForKodi(event.setId());
        syncAll(targets, target -> syncSet(target, set, event.previousName(), memberFile, images));
    }

    void scanSourceOnRealtimeHosts(long sourceId) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            scanSource(sourceId, targets, properties.scanWaitBackground());
        }
    }

    /** For jobs and the realtime sync: they may wait longer for Kodi to finish a running scan. */
    List<KodiSyncResult> refreshMovie(long movieId, List<KodiTarget> targets) {
        return refreshMovie(movieId, targets, properties.scanWaitBackground());
    }

    /** For jobs and the realtime sync: they may wait longer for Kodi to finish a running scan. */
    List<KodiSyncResult> refreshTvShow(long showId, List<KodiTarget> targets) {
        return refreshTvShow(showId, targets, properties.scanWaitBackground());
    }

    private List<KodiSyncResult> refreshMovie(long movieId, List<KodiTarget> targets, Duration maxWait) {
        MovieDetails movie = metadataService.findDetails(movieId);
        return syncAll(targets, target -> refresh(target, movie, maxWait));
    }

    private List<KodiSyncResult> refreshTvShow(long showId, List<KodiTarget> targets, Duration maxWait) {
        TvShowDetails show = tvShowService.findShow(showId);
        return syncAll(targets, target -> refresh(target, show, maxWait));
    }

    private List<KodiSyncResult> refreshEpisode(long episodeId, List<KodiTarget> targets, Duration maxWait) {
        EpisodeDetails episode = tvShowService.findEpisode(episodeId);
        TvShowDetails show = tvShowService.findShow(episode.tvShowId());
        return syncAll(targets, target -> refresh(target, show, episode, maxWait));
    }

    private List<KodiSyncResult> scanSource(long sourceId, List<KodiTarget> targets, Duration maxWait) {
        MediaSource source = sourceService.require(sourceId);
        return syncAll(targets, target -> scan(target, source, maxWait));
    }

    private Outcome refresh(KodiTarget target, MovieDetails movie, Duration maxWait) {
        String title = movie.metadata().title();
        Optional<String> kodiFile = target.pathMapper().toKodiPath(movie.filePath());
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(movieDirectory(movie));
        if (kodiFile.isEmpty() || kodiDirectory.isEmpty()) {
            return notMapped(movie.filePath());
        }
        Optional<Integer> kodiMovieId = kodiClient.findMovie(target.endpoint(), kodiFile.get());
        if (kodiMovieId.isPresent()) {
            kodiClient.refreshMovie(target.endpoint(), kodiMovieId.get());
            return refreshed(title);
        }
        return scanUnknown(target, title, kodiDirectory.get(), maxWait);
    }

    private Outcome refresh(KodiTarget target, TvShowDetails show, Duration maxWait) {
        String title = show.metadata().title();
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(show.directoryPath());
        if (kodiDirectory.isEmpty()) {
            return notMapped(show.directoryPath());
        }
        Optional<Integer> kodiShowId = kodiClient.findTvShow(target.endpoint(), kodiDirectory.get());
        if (kodiShowId.isPresent()) {
            kodiClient.refreshTvShow(target.endpoint(), kodiShowId.get());
            return refreshed(title);
        }
        return scanUnknown(target, title, kodiDirectory.get(), maxWait);
    }

    /** Kodi finds an episode it does not know yet by scanning the TV show folder. */
    private Outcome refresh(KodiTarget target, TvShowDetails show, EpisodeDetails episode, Duration maxWait) {
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
            return refreshed(title);
        }
        return scanUnknown(target, title, kodiDirectory.get(), maxWait);
    }

    private Outcome scanUnknown(KodiTarget target, String title, String kodiDirectory, Duration maxWait) {
        return scanWithSourceCheck(target, kodiDirectory, maxWait,
                LocalizedText.of("kodi.sync.scanUnknown").with("title", title).with("directory", kodiDirectory));
    }

    /**
     * Kodi reports success even for a directory outside its sources and scans nothing. The scan runs anyway because
     * Kodi may know a directory even without an entry in the source list.
     */
    private Outcome scanWithSourceCheck(KodiTarget target, String kodiDirectory, Duration maxWait,
                                        LocalizedText message) {
        Optional<LocalizedText> warning = sourceWarning(target, kodiDirectory);
        scheduler.scan(target.endpoint(), kodiDirectory, maxWait);
        return warning.map(text -> new Outcome(KodiSyncOutcome.NO_KODI_SOURCE, text))
                .orElseGet(() -> new Outcome(KodiSyncOutcome.SCAN_STARTED, message));
    }

    private Optional<LocalizedText> sourceWarning(KodiTarget target, String kodiDirectory) {
        KodiSources sources;
        try {
            sources = kodiClient.videoSources(target.endpoint());
        } catch (KodiException e) {
            log.debug("Could not read the sources of Kodi host '{}': {}", target.name(), e.getMessage());
            return Optional.empty();
        }
        if (sources.contain(kodiDirectory)) {
            return Optional.empty();
        }
        return Optional.of(LocalizedText.of("kodi.sync.noSource")
                .with("directory", kodiDirectory)
                .with("host", target.name())
                .with("sources", sources.describe()));
    }

    private Outcome syncRenamed(KodiTarget target, String localDirectory, String localPrevious, Clean clean) {
        Optional<String> directory = target.pathMapper().toKodiDirectory(localDirectory);
        Optional<String> previous = target.pathMapper().toKodiDirectory(localPrevious);
        if (directory.isEmpty() || previous.isEmpty()) {
            return notMapped(localDirectory);
        }
        Duration maxWait = properties.scanWaitBackground();
        Outcome outcome = scanWithSourceCheck(target, directory.get(), maxWait,
                LocalizedText.of("kodi.sync.renamed").with("directory", directory.get())
                        .with("previous", previous.get()));
        clean.start(target.endpoint(), previous.get(), maxWait);
        return outcome;
    }

    private Outcome syncSet(KodiTarget target, MovieSetDetails set, String previousName, Optional<String> memberFile,
                            Optional<List<MovieSetImage>> images) {
        String name = set.data().name();
        Optional<Integer> kodiSetId = kodiClient.findMovieSet(target.endpoint(), previousName)
                .or(() -> kodiClient.findMovieSet(target.endpoint(), name))
                .or(() -> memberFile.flatMap(target.pathMapper()::toKodiPath)
                        .flatMap(file -> kodiClient.findMovie(target.endpoint(), file))
                        .flatMap(movieId -> kodiClient.movieSetOf(target.endpoint(), movieId)));
        if (kodiSetId.isEmpty()) {
            return new Outcome(KodiSyncOutcome.NOT_IN_KODI,
                    LocalizedText.of("kodi.sync.setNotInKodi").with("name", name));
        }
        Map<String, String> art = art(target, images);
        kodiClient.setMovieSetDetails(target.endpoint(), kodiSetId.get(), name, set.data().overview(), art);
        kodiClient.removeTextures(target.endpoint(), art.values().stream().filter(Objects::nonNull).toList());
        return new Outcome(KodiSyncOutcome.UPDATED, LocalizedText.of("kodi.sync.setUpdated").with("name", name));
    }

    /**
     * Like Ember: existing images as path as Kodi sees it, missing ones are removed in Kodi. Without a storage for set
     * images Kodi's images stay unchanged, as do images outside the path mapping.
     */
    private static Map<String, String> art(KodiTarget target, Optional<List<MovieSetImage>> images) {
        Map<String, String> art = new HashMap<>();
        for (MovieSetImage image : images.orElse(List.of())) {
            if (image.file() == null) {
                art.put(image.kodiName(), null);
            } else {
                target.pathMapper().toKodiPath(image.file().toString())
                        .ifPresent(kodiPath -> art.put(image.kodiName(), kodiPath));
            }
        }
        return art;
    }

    private Outcome scan(KodiTarget target, MediaSource source, Duration maxWait) {
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(source.getPath());
        if (kodiDirectory.isEmpty()) {
            return notMapped(source.getPath());
        }
        return scanWithSourceCheck(target, kodiDirectory.get(), maxWait,
                LocalizedText.of("kodi.sync.scanSource").with("directory", kodiDirectory.get())
                        .with("kind", source.getMediaType() == MediaType.TV_SHOW ? "tvShow" : "movie"));
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
            return new Outcome(KodiSyncOutcome.FAILED, e.localizedText());
        }
    }

    /** For DVD and Blu-ray folders the main file lies one level below the movie folder. */
    private static String movieDirectory(MovieDetails movie) {
        Path directory = Path.of(movie.filePath()).getParent();
        return movie.discFormat() == DiscFormat.FILE ? directory.toString() : directory.getParent().toString();
    }

    private static Outcome notMapped(String path) {
        return new Outcome(KodiSyncOutcome.NOT_MAPPED, LocalizedText.of("kodi.sync.notMapped").with("path", path));
    }

    private static Outcome refreshed(String title) {
        return new Outcome(KodiSyncOutcome.REFRESHED, LocalizedText.of("kodi.sync.refreshed").with("title", title));
    }

    private List<KodiTarget> realtimeTargets() {
        return hostService.targets().stream().filter(KodiTarget::realtimeSync).toList();
    }

    private static void log(KodiSyncResult result) {
        if (result.failed()) {
            log.warn("Kodi sync with '{}' failed: {}", result.host(), Messages.english(result.message()));
        } else {
            log.info("Kodi sync with '{}': {}", result.host(), Messages.english(result.message()));
        }
    }

    private record Outcome(KodiSyncOutcome outcome, LocalizedText message) {
    }

    @FunctionalInterface
    private interface Clean {
        void start(KodiEndpoint endpoint, String directory, Duration maxWait);
    }
}
