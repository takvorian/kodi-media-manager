package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DebouncerTest {

    private static final Duration DELAY = Duration.ofMillis(150);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<String> executed = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void runsOnceForRepeatedSubmissionsOfTheSameKey() throws InterruptedException {
        Debouncer<String> debouncer = new Debouncer<>(executor, DELAY, executed::add);

        debouncer.submit("heat");
        debouncer.submit("ronin");
        debouncer.submit("heat");

        await().atMost(Duration.ofSeconds(2)).until(() -> executed.size() == 2);
        Thread.sleep(DELAY.toMillis() * 2);
        assertThat(executed).containsExactlyInAnyOrder("heat", "ronin");
    }

    @Test
    void keepsWorkingAfterAFailedAction() {
        Debouncer<String> debouncer = new Debouncer<>(executor, Duration.ZERO, key -> {
            executed.add(key);
            if (key.equals("kaputt")) {
                throw new IllegalStateException("Kodi weg");
            }
        });

        debouncer.submit("kaputt");
        await().atMost(Duration.ofSeconds(2)).until(() -> executed.size() == 1);
        debouncer.submit("kaputt");
        debouncer.submit("heat");

        await().atMost(Duration.ofSeconds(2)).until(() -> executed.size() == 3);
        assertThat(executed).containsExactly("kaputt", "kaputt", "heat");
    }
}
