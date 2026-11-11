package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tiktak.kmm.movie.MovieFileReplacedEvent;
import de.tiktak.kmm.movie.MovieFolderScannedEvent;
import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieRemovedEvent;
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSourceScannedEvent;
import de.tiktak.kmm.tvshow.EpisodeFilesChangedEvent;
import de.tiktak.kmm.tvshow.EpisodesRemovedEvent;
import de.tiktak.kmm.tvshow.TvShowFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowFolderScannedEvent;
import de.tiktak.kmm.tvshow.TvShowRemovedEvent;
import de.tiktak.kmm.tvshow.TvShowRenamedEvent;
import de.tiktak.kmm.tvshow.TvShowSourceScannedEvent;

/**
 * Realtime sync as in Ember: after a change to a movie, a TV show or an episode KMM updates it on all Kodi devices
 * with realtime sync; after a scan with new entries Kodi scans the source.
 * The calls run delayed and one after another in the background, so that saving and scraping do not wait for Kodi
 * and several changes to the same movie trigger only one update.
 */
@Component
class KodiRealtimeSync implements DisposableBean {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("kodi-sync").factory());
    private final Debouncer<Long> movies;
    private final Debouncer<Long> sources;
    private final Debouncer<Long> tvShows;
    private final Debouncer<Long> episodes;
    private final Debouncer<MovieFileReplacedEvent> replacedMovies;

    private final KodiSyncService syncService;

    KodiRealtimeSync(KodiSyncService syncService, KodiProperties properties) {
        this.syncService = syncService;
        this.movies = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshMovieOnRealtimeHosts);
        this.sources = new Debouncer<>(executor, properties.syncDelay(), syncService::scanSourceOnRealtimeHosts);
        this.tvShows = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshTvShowOnRealtimeHosts);
        this.episodes = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshEpisodeOnRealtimeHosts);
        this.replacedMovies = new Debouncer<>(executor, properties.syncDelay(),
                syncService::syncReplacedMovieOnRealtimeHosts);
    }

    /** Only after a successful save; without a transaction (images) right away. */
    @TransactionalEventListener(fallbackExecution = true)
    void onMovieFilesChanged(MovieFilesChangedEvent event) {
        movies.submit(event.movieId());
    }

    /** One after another and without debouncing: every renaming has its own folders. */
    @TransactionalEventListener(fallbackExecution = true)
    void onMovieRenamed(MovieRenamedEvent event) {
        executor.execute(() -> syncService.syncRenamedMovieOnRealtimeHosts(event));
    }

    /**
     * Delayed like a source scan, so that ffprobe has written the technical details of the new file to the NFO
     * before Kodi reads it.
     */
    @TransactionalEventListener(fallbackExecution = true)
    void onMovieFileReplaced(MovieFileReplacedEvent event) {
        replacedMovies.submit(event);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onMovieSetChanged(MovieSetChangedEvent event) {
        executor.execute(() -> syncService.syncMovieSetOnRealtimeHosts(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTvShowRenamed(TvShowRenamedEvent event) {
        executor.execute(() -> syncService.syncRenamedTvShowOnRealtimeHosts(event));
    }

    /** Kodi finds removed entries by their files in its own library, even when the files are gone. */
    @TransactionalEventListener(fallbackExecution = true)
    void onMovieRemoved(MovieRemovedEvent event) {
        executor.execute(() -> syncService.removeMovieOnRealtimeHosts(event.title(), event.filePath()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTvShowRemoved(TvShowRemovedEvent event) {
        executor.execute(() -> syncService.removeTvShowOnRealtimeHosts(event.title(), event.directory()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onEpisodesRemoved(EpisodesRemovedEvent event) {
        List<KodiEpisodeRef> episodes = event.episodes().stream()
                .map(episode -> new KodiEpisodeRef(episode.filePath(), episode.season(), episode.episode()))
                .toList();
        executor.execute(() -> syncService.removeEpisodesOnRealtimeHosts(event.showTitle(), episodes));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onSourceScanned(MovieSourceScannedEvent event) {
        if (event.added() > 0) {
            sources.submit(event.sourceId());
        }
    }

    /** Only the folder of the movie, not the whole source. */
    @TransactionalEventListener(fallbackExecution = true)
    void onMovieFolderScanned(MovieFolderScannedEvent event) {
        if (event.moviesChanged()) {
            executor.execute(() -> syncService.scanMovieFolderOnRealtimeHosts(event.directory()));
        }
    }

    /** Only the folder of the TV show or season, not the whole source. */
    @TransactionalEventListener(fallbackExecution = true)
    void onTvShowFolderScanned(TvShowFolderScannedEvent event) {
        if (event.episodesChanged()) {
            executor.execute(() -> syncService.scanTvShowOnRealtimeHosts(event.showId(), event.season()));
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTvShowFilesChanged(TvShowFilesChangedEvent event) {
        tvShows.submit(event.showId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onEpisodeFilesChanged(EpisodeFilesChangedEvent event) {
        episodes.submit(event.episodeId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTvSourceScanned(TvShowSourceScannedEvent event) {
        if (event.added() > 0) {
            sources.submit(event.sourceId());
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
