package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

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
            assertThatThrownBy(throttle::await).isInstanceOf(ScraperException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
