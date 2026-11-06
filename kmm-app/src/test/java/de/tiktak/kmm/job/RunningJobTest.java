package de.tiktak.kmm.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import de.tiktak.kmm.i18n.LocalizedText;

class RunningJobTest {

    private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");
    private static final Duration INTERVAL = Duration.ofMillis(250);

    private final List<JobSnapshot> published = new ArrayList<>();
    private final MutableClock clock = new MutableClock(START);
    private RunningJob job;

    @BeforeEach
    void setUp() {
        JobSnapshot initial = JobSnapshot.started(UUID.randomUUID(), "key", LocalizedText.literal("Scan"), START);
        job = new RunningJob(initial, published::add, clock, INTERVAL);
    }

    @Test
    void publishesStart() {
        job.publishStart();

        assertThat(published).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.status()).isEqualTo(JobStatus.RUNNING);
            assertThat(snapshot.startedAt()).isEqualTo(START);
        });
    }

    @Test
    void throttlesProgressButAlwaysKeepsLatestState() {
        job.publishStart();

        job.reportProgress(1, 10, "eins");
        clock.advance(Duration.ofMillis(100));
        job.reportProgress(2, 10, "zwei");
        clock.advance(Duration.ofMillis(150));
        job.reportProgress(3, 10, "drei");

        assertThat(published).extracting(JobSnapshot::done).containsExactly(0, 3);
        assertThat(job.snapshot().message()).isEqualTo(LocalizedText.literal("drei"));
    }

    @Test
    void publishesFinalStateRegardlessOfThrottle() {
        job.reportProgress(5, 5, "fertig");

        JobSnapshot finished = job.succeed("Ergebnis");

        assertThat(published.getLast()).isEqualTo(finished);
        assertThat(finished.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(finished.result()).isEqualTo("Ergebnis");
        assertThat(finished.finishedAt()).isEqualTo(START);
    }

    @Test
    void recordsFailureWithMessageAndKeepsProgress() {
        job.reportProgress(2, 5, "läuft");

        JobSnapshot failed = job.fail(LocalizedText.literal("kaputt"));

        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.message()).isEqualTo(LocalizedText.literal("kaputt"));
        assertThat(failed.done()).isEqualTo(2);
    }

    @Test
    void signalsCancellationToTheTask() {
        assertThatNoException().isThrownBy(job::throwIfCancellationRequested);

        job.requestCancellation();

        assertThat(job.isCancellationRequested()).isTrue();
        assertThatExceptionOfType(JobCancelledException.class).isThrownBy(job::throwIfCancellationRequested);
        assertThat(job.cancel().status()).isEqualTo(JobStatus.CANCELLED);
    }
}
