package de.tiktak.kmm.plugin.api;

import java.time.Duration;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Keeps a gap between the requests to a web page without an API so a job does not burden it.
 */
public final class RequestThrottle {

    private final long delayNanos;
    private long nextRequestNanos = System.nanoTime();

    public RequestThrottle(Duration delay) {
        delayNanos = delay.toNanos();
    }

    /**
     * Waits until the next request is allowed.
     *
     * @throws ScraperException if the wait is interrupted
     */
    public synchronized void await() {
        long waitNanos = nextRequestNanos - System.nanoTime();
        if (waitNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScraperException(LocalizedText.of("scraper.requestInterrupted"), e);
            }
        }
        nextRequestNanos = System.nanoTime() + delayNanos;
    }
}
