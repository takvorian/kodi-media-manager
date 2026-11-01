package de.tiktak.kmm.kodi.rpc;

import java.time.Duration;

import lombok.RequiredArgsConstructor;

/**
 * Startet Scans und Bereinigungen erst, wenn Kodi damit fertig ist. Kodi kennt keine Warteschlange dafür: Ein Scan
 * während eines laufenden Scans bricht diesen ab, ohne selbst zu starten, und ein Bereinigen währenddessen verwirft
 * Kodi. Beides meldet Kodi dem Aufrufer als Erfolg.
 */
@RequiredArgsConstructor
public class KodiLibraryScheduler {

    private final KodiClient client;
    private final Duration pollInterval;

    /**
     * Kodi durchsucht das Verzeichnis, sobald es frei ist.
     *
     * @param directory Verzeichnis aus Sicht von Kodi mit abschließendem Trennzeichen
     * @param maxWait   so lange wartet KMM höchstens auf das Ende eines laufenden Scans
     * @throws KodiException wenn Kodi nach dieser Zeit noch beschäftigt ist
     */
    public void scan(KodiEndpoint endpoint, String directory, Duration maxWait) {
        whenIdle(endpoint, maxWait, () -> client.scan(endpoint, directory));
    }

    /** Wie {@link #scan}: Kodi entfernt Filme im Verzeichnis, deren Dateien es nicht mehr gibt. */
    public void cleanMovies(KodiEndpoint endpoint, String directory, Duration maxWait) {
        whenIdle(endpoint, maxWait, () -> client.cleanMovies(endpoint, directory));
    }

    /** Wie {@link #scan}: Kodi entfernt Serien und Episoden im Verzeichnis, deren Dateien es nicht mehr gibt. */
    public void cleanTvShows(KodiEndpoint endpoint, String directory, Duration maxWait) {
        whenIdle(endpoint, maxWait, () -> client.cleanTvShows(endpoint, directory));
    }

    private void whenIdle(KodiEndpoint endpoint, Duration maxWait, Runnable start) {
        long startedAt = System.nanoTime();
        while (!startIfIdle(endpoint, start)) {
            if (System.nanoTime() - startedAt >= maxWait.toNanos()) {
                throw new KodiException("Kodi liest gerade seine Bibliothek ein. Versuche es danach noch einmal.");
            }
            pause();
        }
    }

    /** Prüfen und Starten gehören zusammen, sonst bricht der Scan eines anderen Threads diesen hier gleich wieder ab. */
    private synchronized boolean startIfIdle(KodiEndpoint endpoint, Runnable start) {
        if (client.isScanning(endpoint)) {
            return false;
        }
        start.run();
        return true;
    }

    private void pause() {
        try {
            Thread.sleep(pollInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KodiException("Das Warten auf Kodi wurde abgebrochen.", e);
        }
    }
}
