package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.i18n.LocalizedText;

class RequestThrottleTest {

    @Test
    void waitsBetweenRequestsButNotBeforeTheFirst() {
        RequestThrottle throttle = new RequestThrottle(Duration.ofMillis(50));

        long start = System.nanoTime();
        throttle.await();
        throttle.await();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(45));
    }

    @Test
    void reportsAnInterruptedWait() {
        RequestThrottle throttle = new RequestThrottle(Duration.ofMinutes(1));
        throttle.await();

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(throttle::await).isInstanceOfSatisfying(ScraperException.class, e ->
                    assertThat(e.localizedText()).isEqualTo(LocalizedText.of("scraper.requestInterrupted")));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
