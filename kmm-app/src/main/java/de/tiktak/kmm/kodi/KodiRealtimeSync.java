package de.tiktak.kmm.kodi;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieSourceScannedEvent;

/**
 * Echtzeit-Sync wie in Ember: Nach einer Änderung an einem Film aktualisiert KMM ihn auf allen
 * Kodi-Geräten mit Echtzeit-Sync, nach einem Scan mit neuen Filmen durchsucht Kodi die Quelle.
 * Die Aufrufe laufen verzögert und nacheinander im Hintergrund, damit Speichern und Scrapen nicht
 * auf Kodi warten und mehrere Änderungen am selben Film nur eine Aktualisierung auslösen.
 */
@Component
class KodiRealtimeSync implements DisposableBean {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("kodi-sync").factory());
    private final Debouncer<Long> movies;
    private final Debouncer<Long> sources;

    KodiRealtimeSync(KodiSyncService syncService, KodiProperties properties) {
        this.movies = new Debouncer<>(executor, properties.syncDelay(), syncService::refreshMovieOnRealtimeHosts);
        this.sources = new Debouncer<>(executor, properties.syncDelay(), syncService::scanSourceOnRealtimeHosts);
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

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
