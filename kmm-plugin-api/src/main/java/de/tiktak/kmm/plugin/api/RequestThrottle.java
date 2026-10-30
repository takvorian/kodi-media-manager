package de.tiktak.kmm.plugin.api;

import java.time.Duration;

/**
 * Hält Abstand zwischen den Anfragen an eine Webseite ohne API, damit ein Job sie nicht belastet.
 */
public final class RequestThrottle {

    private final long delayNanos;
    private long nextRequestNanos = System.nanoTime();

    public RequestThrottle(Duration delay) {
        delayNanos = delay.toNanos();
    }

    /**
     * Wartet, bis die nächste Anfrage erlaubt ist.
     *
     * @throws ScraperException wenn das Warten unterbrochen wird
     */
    public synchronized void await() {
        long waitNanos = nextRequestNanos - System.nanoTime();
        if (waitNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ScraperException("Die Anfrage wurde unterbrochen.", e);
            }
        }
        nextRequestNanos = System.nanoTime() + delayNanos;
    }
}
