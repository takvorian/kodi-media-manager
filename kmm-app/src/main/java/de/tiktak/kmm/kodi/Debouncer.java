package de.tiktak.kmm.kodi;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Führt eine Aktion je Schlüssel erst aus, wenn er eine Weile nicht mehr gemeldet wurde. So lösen
 * mehrere Änderungen kurz hintereinander (z. B. Metadaten und danach Bilder) nur eine Aktion aus.
 */
@Slf4j
final class Debouncer<K> {

    private final ScheduledExecutorService executor;
    private final Duration delay;
    private final Consumer<K> action;
    private final Map<K, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    Debouncer(ScheduledExecutorService executor, Duration delay, Consumer<K> action) {
        this.executor = executor;
        this.delay = delay;
        this.action = action;
    }

    /** Plant die Aktion für den Schlüssel (neu) ein; eine noch wartende Ausführung entfällt. */
    void submit(K key) {
        pending.compute(key, (k, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            AtomicReference<ScheduledFuture<?>> self = new AtomicReference<>();
            ScheduledFuture<?> future = executor.schedule(() -> run(key, self), delay.toMillis(),
                    TimeUnit.MILLISECONDS);
            self.set(future);
            return future;
        });
    }

    /** Entfernt nur die eigene Planung; der Vergleich läuft unter derselben Sperre wie {@link #submit}. */
    private void run(K key, AtomicReference<ScheduledFuture<?>> self) {
        pending.computeIfPresent(key, (k, current) -> current == self.get() ? null : current);
        try {
            action.accept(key);
        } catch (RuntimeException e) {
            log.warn("Delayed action for {} failed", key, e);
        }
    }
}
