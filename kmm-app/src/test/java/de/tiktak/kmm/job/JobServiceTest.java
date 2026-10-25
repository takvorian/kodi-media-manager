package de.tiktak.kmm.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class JobServiceTest {

    private static final Instant START = Instant.parse("2026-10-25T12:00:00Z");
    private static final int HISTORY_SIZE = 2;

    private final QueuedExecutor executor = new QueuedExecutor();
    private final List<JobSnapshot> events = new ArrayList<>();
    private final MutableClock clock = new MutableClock(START);
    private JobService service;

    @BeforeEach
    void setUp() {
        ApplicationEventPublisher publisher = event -> events.add(((JobChangedEvent) event).job());
        service = new JobService(executor, publisher, clock, new JobProperties(HISTORY_SIZE, Duration.ZERO));
    }

    @Test
    void startsJobInBackgroundAndReportsItAsRunning() {
        JobSnapshot started = service.submit("scan:1", "Scan Filme", context -> "fertig");

        assertThat(started.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(service.find(started.id())).isEqualTo(started);
        assertThat(service.findAll()).containsExactly(started);
        assertThat(events).containsExactly(started);
    }

    @Test
    void storesResultOfSuccessfulJob() {
        JobSnapshot started = service.submit("scan:1", "Scan Filme", context -> {
            context.reportProgress(3, 3, "fast fertig");
            return "fertig";
        });

        runSubmittedTasks();

        JobSnapshot finished = service.find(started.id());
        assertThat(finished.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(finished.result()).isEqualTo("fertig");
        assertThat(events).extracting(JobSnapshot::status)
                .containsExactly(JobStatus.RUNNING, JobStatus.RUNNING, JobStatus.SUCCEEDED);
    }

    @Test
    void recordsFailureMessage() {
        JobSnapshot started = service.submit("scan:1", "Scan", context -> {
            throw new IllegalStateException("Verzeichnis weg");
        });

        runSubmittedTasks();

        assertThat(service.find(started.id())).satisfies(job -> {
            assertThat(job.status()).isEqualTo(JobStatus.FAILED);
            assertThat(job.message()).isEqualTo("Verzeichnis weg");
        });
    }

    @Test
    void usesGenericMessageForExceptionsWithoutMessage() {
        JobSnapshot started = service.submit("scan:1", "Scan", context -> {
            throw new IllegalStateException();
        });

        runSubmittedTasks();

        assertThat(service.find(started.id()).message()).isEqualTo("Unerwarteter Fehler");
    }

    @Test
    void cancelsRunningJobOnRequest() {
        JobSnapshot started = service.submit("scan:1", "Scan", context -> {
            context.throwIfCancellationRequested();
            return "nicht erreicht";
        });

        service.cancel(started.id());
        runSubmittedTasks();

        assertThat(service.find(started.id()).status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void returnsFinishedJobUnchangedWhenCancelledLate() {
        JobSnapshot started = service.submit("scan:1", "Scan", context -> "fertig");
        runSubmittedTasks();

        assertThat(service.cancel(started.id()).status()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void rejectsSecondJobWithSameKeyWhileFirstIsRunning() {
        JobSnapshot first = service.submit("scan:1", "Scan Filme", context -> "fertig");

        assertThatThrownBy(() -> service.submit("scan:1", "Scan Filme", context -> "fertig"))
                .isInstanceOfSatisfying(JobAlreadyRunningException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getBody().getProperties()).containsEntry("jobId", first.id());
                });
    }

    @Test
    void allowsSameKeyAgainAfterJobFinished() {
        service.submit("scan:1", "Scan", context -> "fertig");
        runSubmittedTasks();

        assertThat(service.submit("scan:1", "Scan", context -> "fertig").status()).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void listsRunningJobsBeforeFinishedOnesAndLimitsHistory() {
        JobSnapshot oldest = service.submit("a", "A", context -> "a");
        clock.advance(Duration.ofSeconds(1));
        JobSnapshot middle = service.submit("b", "B", context -> "b");
        clock.advance(Duration.ofSeconds(1));
        JobSnapshot newest = service.submit("c", "C", context -> "c");
        runSubmittedTasks();
        JobSnapshot running = service.submit("d", "D", context -> "d");

        assertThat(service.findAll()).extracting(JobSnapshot::id)
                .containsExactly(running.id(), newest.id(), middle.id());
        assertThatThrownBy(() -> service.find(oldest.id())).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void rejectsUnknownJob() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.find(unknown))
                .isInstanceOfSatisfying(JobNotFoundException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.cancel(unknown)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void requestsCancellationOfAllJobsOnShutdown() {
        JobSnapshot started = service.submit("scan:1", "Scan", context -> {
            context.throwIfCancellationRequested();
            return "nicht erreicht";
        });

        service.cancelAllOnShutdown();
        runSubmittedTasks();

        assertThat(service.find(started.id()).status()).isEqualTo(JobStatus.CANCELLED);
    }

    private void runSubmittedTasks() {
        executor.runAll();
    }
}
