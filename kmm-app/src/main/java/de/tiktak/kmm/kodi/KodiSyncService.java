package de.tiktak.kmm.kodi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.artwork.MovieSetArtworkService;
import de.tiktak.kmm.artwork.MovieSetImage;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiEpisode;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.kodi.rpc.KodiLibraryScheduler;
import de.tiktak.kmm.kodi.rpc.KodiSources;
import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieFileReplacedEvent;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSetDetails;
import de.tiktak.kmm.movie.MovieSetProblemException;
import de.tiktak.kmm.movie.MovieSetService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.tvshow.EpisodeDetails;
import de.tiktak.kmm.tvshow.EpisodeSummary;
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
            refreshTvShow(showId, targets, properties.scanWaitBackground());
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

    /**
     * After the video file of a movie was replaced in place: Kodi forgets the entry of the old file and scans the
     * folder for the new one. Watched status and rating come back from the NFO.
     */
    void syncReplacedMovieOnRealtimeHosts(MovieFileReplacedEvent event) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            MovieDetails movie = metadataService.findDetails(event.movieId());
            syncAll(targets, target -> syncReplaced(target, movie, event.previousFilePath()));
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

    /**
     * Removes a movie from Kodi on all devices with realtime sync. Called before KMM deletes its entry and the files,
     * because Kodi finds the movie by its file.
     */
    public List<KodiSyncResult> removeMovieOnRealtimeHosts(String title, String filePath) {
        return syncAll(realtimeTargets(), target -> removeMovie(target, title, filePath));
    }

    /** Removes a TV show with all its episodes from Kodi on all devices with realtime sync. */
    public List<KodiSyncResult> removeTvShowOnRealtimeHosts(String title, String directory) {
        return syncAll(realtimeTargets(), target -> removeTvShow(target, title, directory));
    }

    /**
     * Removes episodes from Kodi on all devices with realtime sync, e.g. all episodes of a season or of a
     * multi-episode file.
     */
    public List<KodiSyncResult> removeEpisodesOnRealtimeHosts(String showTitle, List<KodiEpisodeRef> episodes) {
        return syncAll(realtimeTargets(), target -> removeEpisodes(target, showTitle, episodes));
    }

    /** After KMM scanned a TV show or season folder: Kodi scans the folder and cleans it up. */
    void scanTvShowOnRealtimeHosts(long showId, OptionalInt season) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            scanTvShow(showId, season, targets, KodiProgress.NONE);
        }
    }

    /** After KMM scanned the folder of a movie: Kodi scans the folder and cleans it up. */
    void scanMovieFolderOnRealtimeHosts(String directory) {
        List<KodiTarget> targets = realtimeTargets();
        if (!targets.isEmpty()) {
            syncAll(targets, target -> scanMovieFolder(target, directory));
        }
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

    private List<KodiSyncResult> refreshMovie(long movieId, List<KodiTarget> targets, Duration maxWait) {
        MovieDetails movie = metadataService.findDetails(movieId);
        return syncAll(targets, target -> refresh(target, movie, maxWait));
    }

    private List<KodiSyncResult> refreshTvShow(long showId, List<KodiTarget> targets, Duration maxWait) {
        TvShowDetails show = tvShowService.findShow(showId);
        return syncAll(targets, target -> refresh(target, show, maxWait));
    }

    /**
     * Updates the TV show with all its episodes, or only the episodes of one season, episode by episode, so Kodi keeps
     * its IDs, resume points and watched status. Kodi finds episodes it does not know yet by one scan of the TV show
     * folder.
     *
     * @param season   season number, or empty for the whole TV show including {@code tvshow.nfo}
     * @param progress learns about every episode handled on every device
     */
    List<KodiSyncResult> refreshTvShowWithEpisodes(long showId, OptionalInt season, List<KodiTarget> targets,
                                                   KodiProgress progress) {
        TvShowDetails show = tvShowService.findShow(showId);
        List<EpisodeSummary> episodes = show.episodes().stream()
                .filter(episode -> season.isEmpty() || episode.season() == season.getAsInt())
                .toList();
        Steps steps = new Steps(targets.size() * episodes.size(), progress);
        return syncAll(targets, target -> refresh(target, show, season, episodes, steps));
    }

    /**
     * Kodi scans the folder of the TV show, or of one season, for new episodes and then removes the entries whose
     * files no longer exist there. Kodi takes a season folder it does not know yet for a TV show of its own, so then
     * it scans the TV show folder instead.
     *
     * @param season   season number, or empty for the whole TV show
     * @param progress learns about every device that is done
     */
    List<KodiSyncResult> scanTvShow(long showId, OptionalInt season, List<KodiTarget> targets,
                                    KodiProgress progress) {
        TvShowDetails show = tvShowService.findShow(showId);
        Optional<String> seasonDirectory = season.isEmpty() ? Optional.empty()
                : seasonDirectory(show, season.getAsInt());
        Steps steps = new Steps(targets.size(), progress);
        return syncAll(targets, target -> {
            try {
                return scan(target, show, seasonDirectory);
            } finally {
                steps.next();
            }
        });
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

    private Outcome refresh(KodiTarget target, TvShowDetails show, OptionalInt season, List<EpisodeSummary> episodes,
                            Steps steps) {
        String title = show.metadata().title();
        Duration maxWait = properties.scanWaitBackground();
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(show.directoryPath());
        if (kodiDirectory.isEmpty()) {
            steps.skip(episodes.size());
            return notMapped(show.directoryPath());
        }
        Optional<Integer> kodiShowId = kodiClient.findTvShow(target.endpoint(), kodiDirectory.get());
        if (kodiShowId.isEmpty()) {
            steps.skip(episodes.size());
            return scanUnknown(target, title, kodiDirectory.get(), maxWait);
        }
        if (season.isEmpty()) {
            kodiClient.refreshTvShow(target.endpoint(), kodiShowId.get());
        }
        List<KodiEpisode> known = kodiClient.episodesOf(target.endpoint(), kodiShowId.get());
        int refreshed = 0;
        for (EpisodeSummary episode : episodes) {
            Optional<Integer> kodiEpisodeId = target.pathMapper().toKodiPath(episode.filePath())
                    .flatMap(file -> known.stream()
                            .filter(candidate -> candidate.is(file, episode.season(), episode.episode()))
                            .map(KodiEpisode::id)
                            .findFirst());
            kodiEpisodeId.ifPresent(id -> kodiClient.refreshEpisode(target.endpoint(), id));
            refreshed += kodiEpisodeId.isPresent() ? 1 : 0;
            steps.next();
        }
        LocalizedText message = season.isEmpty()
                ? LocalizedText.of("kodi.sync.tvShowRefreshed").with("title", title).with("count", refreshed)
                : LocalizedText.of("kodi.sync.seasonRefreshed").with("title", title)
                        .with("season", season.getAsInt()).with("count", refreshed);
        int unknown = episodes.size() - refreshed;
        if (unknown == 0) {
            return new Outcome(KodiSyncOutcome.REFRESHED, message);
        }
        return scanWithSourceCheck(target, kodiDirectory.get(), maxWait,
                LocalizedText.of("kodi.sync.unknownEpisodesScan").with("refreshed", message)
                        .with("count", unknown).with("directory", kodiDirectory.get()));
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

    private Outcome removeMovie(KodiTarget target, String title, String filePath) {
        Optional<String> kodiFile = target.pathMapper().toKodiPath(filePath);
        if (kodiFile.isEmpty()) {
            return notMapped(filePath);
        }
        Optional<Integer> kodiMovieId = kodiClient.findMovie(target.endpoint(), kodiFile.get());
        kodiMovieId.ifPresent(id -> kodiClient.removeMovie(target.endpoint(), id));
        return kodiMovieId.isPresent() ? removed(title) : notInKodi(title);
    }

    private Outcome removeTvShow(KodiTarget target, String title, String directory) {
        Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(directory);
        if (kodiDirectory.isEmpty()) {
            return notMapped(directory);
        }
        Optional<Integer> kodiShowId = kodiClient.findTvShow(target.endpoint(), kodiDirectory.get());
        kodiShowId.ifPresent(id -> kodiClient.removeTvShow(target.endpoint(), id));
        return kodiShowId.isPresent() ? removed(title) : notInKodi(title);
    }

    private Outcome removeEpisodes(KodiTarget target, String showTitle, List<KodiEpisodeRef> episodes) {
        int removedCount = 0;
        for (KodiEpisodeRef episode : episodes) {
            Optional<String> kodiFile = target.pathMapper().toKodiPath(episode.filePath());
            if (kodiFile.isEmpty()) {
                return notMapped(episode.filePath());
            }
            Optional<Integer> kodiEpisodeId = kodiClient.findEpisode(target.endpoint(), kodiFile.get(),
                    episode.season(), episode.episode());
            if (kodiEpisodeId.isPresent()) {
                kodiClient.removeEpisode(target.endpoint(), kodiEpisodeId.get());
                removedCount++;
            }
        }
        if (removedCount == 0) {
            return notInKodi(showTitle);
        }
        return new Outcome(KodiSyncOutcome.REMOVED, LocalizedText.of("kodi.sync.episodesRemoved")
                .with("title", showTitle).with("count", removedCount));
    }

    private Outcome scanMovieFolder(KodiTarget target, String localDirectory) {
        Optional<String> directory = target.pathMapper().toKodiDirectory(localDirectory);
        if (directory.isEmpty()) {
            return notMapped(localDirectory);
        }
        Duration maxWait = properties.scanWaitBackground();
        Outcome outcome = scanWithSourceCheck(target, directory.get(), maxWait,
                LocalizedText.of("kodi.sync.scanMovieFolder").with("directory", directory.get()));
        scheduler.cleanMovies(target.endpoint(), directory.get(), maxWait);
        return outcome;
    }

    private Outcome scan(KodiTarget target, TvShowDetails show, Optional<String> seasonDirectory) {
        Optional<String> showDirectory = target.pathMapper().toKodiDirectory(show.directoryPath());
        if (showDirectory.isEmpty()) {
            return notMapped(show.directoryPath());
        }
        String directory = seasonDirectory.flatMap(target.pathMapper()::toKodiDirectory)
                .filter(candidate -> kodiKnowsFolder(target, showDirectory.get(), candidate))
                .orElse(showDirectory.get());
        removeVanishedEpisodes(target, show, showDirectory.get(), directory);
        Duration maxWait = properties.scanWaitBackground();
        Outcome outcome = scanWithSourceCheck(target, directory, maxWait,
                LocalizedText.of("kodi.sync.scanTvShow").with("title", show.metadata().title())
                        .with("directory", directory));
        scheduler.cleanTvShows(target.endpoint(), directory, maxWait);
        return outcome;
    }

    /**
     * Removes the episodes in the directory from Kodi whose files no longer exist, such as after replacing an episode
     * with a file of another name. Kodi's cleanup of a folder does not reliably catch them. Without access to the TV
     * show folder, for instance while the share is offline, nothing is removed.
     *
     * @param kodiDirectory the folder Kodi scans, as Kodi sees it
     */
    private void removeVanishedEpisodes(KodiTarget target, TvShowDetails show, String kodiShowDirectory,
                                        String kodiDirectory) {
        Path localDirectory = Path.of(show.directoryPath());
        if (!Files.isDirectory(localDirectory)) {
            return;
        }
        Optional<Integer> kodiShowId = kodiClient.findTvShow(target.endpoint(), kodiShowDirectory);
        if (kodiShowId.isEmpty()) {
            return;
        }
        List<KodiEpisode> candidates = kodiClient.episodesOf(target.endpoint(), kodiShowId.get()).stream()
                .filter(episode -> episode.liesBelow(kodiDirectory))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        Optional<Set<String>> existing = kodiFilesBelow(target, localDirectory);
        if (existing.isEmpty()) {
            return;
        }
        List<KodiEpisode> vanished = candidates.stream().filter(episode -> !episode.isAmong(existing.get())).toList();
        vanished.forEach(episode -> kodiClient.removeEpisode(target.endpoint(), episode.id()));
        if (!vanished.isEmpty()) {
            log.info("Removed {} episodes of '{}' without file from Kodi host '{}': {}", vanished.size(),
                    show.metadata().title(), target.name(), vanished.stream().map(KodiEpisode::file).toList());
        }
    }

    /** @return all files below the local directory as Kodi sees them, or empty if the folder cannot be read */
    private static Optional<Set<String>> kodiFilesBelow(KodiTarget target, Path localDirectory) {
        try (Stream<Path> files = Files.walk(localDirectory)) {
            return Optional.of(files.filter(Files::isRegularFile)
                    .map(file -> target.pathMapper().toKodiPath(file.toString()))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet()));
        } catch (IOException | UncheckedIOException e) {
            log.warn("Folder {} not read, so no vanished episodes are removed from Kodi: {}", localDirectory,
                    e.getMessage());
            return Optional.empty();
        }
    }

    private boolean kodiKnowsFolder(KodiTarget target, String showDirectory, String directory) {
        return kodiClient.findTvShow(target.endpoint(), showDirectory)
                .map(showId -> kodiClient.episodesOf(target.endpoint(), showId).stream()
                        .anyMatch(episode -> episode.liesIn(directory)))
                .orElse(false);
    }

    /** @return the folder below the TV show folder that holds all episodes of the season, if there is one */
    private static Optional<String> seasonDirectory(TvShowDetails show, int season) {
        Set<Path> directories = show.episodes().stream()
                .filter(episode -> episode.season() == season)
                .map(episode -> Path.of(episode.filePath()).getParent())
                .collect(Collectors.toSet());
        Path showDirectory = Path.of(show.directoryPath());
        return directories.size() == 1
                ? directories.stream()
                        .filter(directory -> directory.startsWith(showDirectory) && !directory.equals(showDirectory))
                        .map(Path::toString)
                        .findFirst()
                : Optional.empty();
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

    private Outcome syncReplaced(KodiTarget target, MovieDetails movie, String previousFilePath) {
        Optional<String> previous = target.pathMapper().toKodiPath(previousFilePath);
        Optional<String> directory = target.pathMapper().toKodiDirectory(movieDirectory(movie));
        if (previous.isEmpty() || directory.isEmpty()) {
            return notMapped(movie.filePath());
        }
        kodiClient.findMovie(target.endpoint(), previous.get())
                .ifPresent(kodiMovieId -> kodiClient.removeMovie(target.endpoint(), kodiMovieId));
        return scanWithSourceCheck(target, directory.get(), properties.scanWaitBackground(),
                LocalizedText.of("kodi.sync.replaced").with("title", movie.metadata().title())
                        .with("directory", directory.get()));
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

    private static Outcome removed(String title) {
        return new Outcome(KodiSyncOutcome.REMOVED, LocalizedText.of("kodi.sync.removed").with("title", title));
    }

    private static Outcome notInKodi(String title) {
        return new Outcome(KodiSyncOutcome.NOT_IN_KODI, LocalizedText.of("kodi.sync.notInKodi").with("title", title));
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

    /** Counts the steps of all devices; a device that is skipped counts its steps as done. */
    private static final class Steps {

        private final int total;
        private final KodiProgress progress;
        private int done;

        private Steps(int total, KodiProgress progress) {
            this.total = total;
            this.progress = progress;
        }

        void next() {
            skip(1);
        }

        void skip(int count) {
            done += count;
            progress.report(done, total);
        }
    }

    @FunctionalInterface
    private interface Clean {
        void start(KodiEndpoint endpoint, String directory, Duration maxWait);
    }
}
