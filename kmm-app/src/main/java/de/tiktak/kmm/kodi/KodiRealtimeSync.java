package de.tiktak.kmm.kodi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSourceScannedEvent;
import de.tiktak.kmm.tvshow.EpisodeFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowFilesChangedEvent;
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

    private final KodiSyncService syncService;

    KodiRealtimeSync(KodiSyncService syncService, KodiProperties properties) {
        this.syncService = syncService;
        this.movies = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshMovieOnRealtimeHosts);
        this.sources = new Debouncer<>(executor, properties.syncDelay(), syncService::scanSourceOnRealtimeHosts);
        this.tvShows = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshTvShowOnRealtimeHosts);
        this.episodes = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshEpisodeOnRealtimeHosts);
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

    @TransactionalEventListener(fallbackExecution = true)
    void onMovieSetChanged(MovieSetChangedEvent event) {
        executor.execute(() -> syncService.syncMovieSetOnRealtimeHosts(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTvShowRenamed(TvShowRenamedEvent event) {
        executor.execute(() -> syncService.syncRenamedTvShowOnRealtimeHosts(event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onSourceScanned(MovieSourceScannedEvent event) {
        if (event.added() > 0) {
            sources.submit(event.sourceId());
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
