package de.tiktak.kmm.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * State of a running job. Progress reports are passed on throttled so fast jobs do not flood the user interface
 * with thousands of events; the final state is always reported.
 */
final class RunningJob implements JobContext {

    private final AtomicReference<JobSnapshot> snapshot;
    private final Consumer<JobSnapshot> publisher;
    private final Clock clock;
    private final Duration progressInterval;
    private volatile boolean cancellationRequested;
    private Instant lastPublished = Instant.MIN;

    RunningJob(JobSnapshot initial, Consumer<JobSnapshot> publisher, Clock clock, Duration progressInterval) {
        this.snapshot = new AtomicReference<>(initial);
        this.publisher = publisher;
        this.clock = clock;
        this.progressInterval = progressInterval;
    }

    JobSnapshot snapshot() {
        return snapshot.get();
    }

    @Override
    public void reportProgress(int done, int total, LocalizedText message) {
        JobSnapshot updated = snapshot.updateAndGet(current -> current.withProgress(done, total, message));
        publishThrottled(updated);
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    void requestCancellation() {
        cancellationRequested = true;
    }

    JobSnapshot succeed(Object result) {
        return finish(snapshot.updateAndGet(current -> current.succeeded(result, clock.instant())));
    }

    JobSnapshot fail(LocalizedText error) {
        return finish(snapshot.updateAndGet(current -> current.failed(error, clock.instant())));
    }

    JobSnapshot cancel() {
        return finish(snapshot.updateAndGet(current -> current.cancelled(clock.instant())));
    }

    void publishStart() {
        publish(snapshot.get());
    }

    private JobSnapshot finish(JobSnapshot finished) {
        publish(finished);
        return finished;
    }

    private synchronized void publishThrottled(JobSnapshot updated) {
        Instant now = clock.instant();
        if (Duration.between(lastPublished, now).compareTo(progressInterval) >= 0) {
            lastPublished = now;
            publisher.accept(updated);
        }
    }

    private synchronized void publish(JobSnapshot current) {
        lastPublished = clock.instant();
        publisher.accept(current);
    }
}
