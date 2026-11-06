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
 * Runs an action per key only once the key has not been reported for a while. This way several changes in quick
 * succession (e.g. metadata and then images) trigger only one action.
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

    /** (Re)schedules the action for the key; an execution that is still waiting is dropped. */
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

    /** Removes only its own schedule; the comparison runs under the same lock as {@link #submit}. */
    private void run(K key, AtomicReference<ScheduledFuture<?>> self) {
        pending.computeIfPresent(key, (k, current) -> current == self.get() ? null : current);
        try {
            action.accept(key);
        } catch (RuntimeException e) {
            log.warn("Delayed action for {} failed", key, e);
        }
    }
}
