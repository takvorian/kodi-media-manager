package de.tiktak.kmm.kodi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieSourceScannedEvent;
import de.tiktak.kmm.tvshow.EpisodeFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowSourceScannedEvent;

/**
 * Echtzeit-Sync wie in Ember: Nach einer Änderung an einem Film, einer Serie oder Episode aktualisiert
 * KMM sie auf allen Kodi-Geräten mit Echtzeit-Sync, nach einem Scan mit neuen Einträgen durchsucht Kodi
 * die Quelle.
 * Die Aufrufe laufen verzögert und nacheinander im Hintergrund, damit Speichern und Scrapen nicht
 * auf Kodi warten und mehrere Änderungen am selben Film nur eine Aktualisierung auslösen.
 */
@Component
class KodiRealtimeSync implements DisposableBean {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("kodi-sync").factory());
    private final Debouncer<Long> movies;
    private final Debouncer<Long> sources;
    private final Debouncer<Long> tvShows;
    private final Debouncer<Long> episodes;

    KodiRealtimeSync(KodiSyncService syncService, KodiProperties properties) {
        this.movies = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshMovieOnRealtimeHosts);
        this.sources = new Debouncer<>(executor, properties.syncDelay(), syncService::scanSourceOnRealtimeHosts);
        this.tvShows = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshTvShowOnRealtimeHosts);
        this.episodes = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshEpisodeOnRealtimeHosts);
    }

    /** Erst nach erfolgreichem Speichern; ohne Transaktion (Bilder) sofort. */
    @TransactionalEventListener(fallbackExecution = true)
    void onMovieFilesChanged(MovieFilesChangedEvent event) {
        movies.submit(event.movieId());
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
