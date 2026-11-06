package de.tiktak.kmm.kodi.rpc;

import java.time.Duration;

import de.tiktak.kmm.i18n.LocalizedText;
import lombok.RequiredArgsConstructor;

/**
 * Starts scans and cleanups only once Kodi is done with the previous one. Kodi has no queue for them: a scan
 * during a running scan cancels it without starting itself, and Kodi discards a cleanup during a scan. Kodi reports
 * both to the caller as success.
 */
@RequiredArgsConstructor
public class KodiLibraryScheduler {

    private final KodiClient client;
    private final Duration pollInterval;

    /**
     * Kodi scans the directory as soon as it is idle.
     *
     * @param directory directory as Kodi sees it, with a trailing separator
     * @param maxWait   how long KMM waits at most for a running scan to end
     * @throws KodiException if Kodi is still busy after that time
     */
    public void scan(KodiEndpoint endpoint, String directory, Duration maxWait) {
        whenIdle(endpoint, maxWait, () -> client.scan(endpoint, directory));
    }

    /** Like {@link #scan}: Kodi removes movies in the directory whose files no longer exist. */
    public void cleanMovies(KodiEndpoint endpoint, String directory, Duration maxWait) {
        whenIdle(endpoint, maxWait, () -> client.cleanMovies(endpoint, directory));
    }

    /** Like {@link #scan}: Kodi removes TV shows and episodes in the directory whose files no longer exist. */
    public void cleanTvShows(KodiEndpoint endpoint, String directory, Duration maxWait) {
        whenIdle(endpoint, maxWait, () -> client.cleanTvShows(endpoint, directory));
    }

    private void whenIdle(KodiEndpoint endpoint, Duration maxWait, Runnable start) {
        long startedAt = System.nanoTime();
        while (!startIfIdle(endpoint, start)) {
            if (System.nanoTime() - startedAt >= maxWait.toNanos()) {
                throw new KodiException(LocalizedText.of("kodiRpc.libraryBusy"));
            }
            pause();
        }
    }

    /** Checking and starting belong together, otherwise another thread's scan would cancel this one right away. */
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
            throw new KodiException(LocalizedText.of("kodiRpc.waitInterrupted"), e);
        }
    }
}
