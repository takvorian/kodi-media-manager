package de.tiktak.kmm.job;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts background jobs and keeps their state. Running jobs and the most recently finished ones are kept in memory;
 * after a restart the list starts empty. At most one job runs per business key at a time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final LocalizedText UNEXPECTED_ERROR = LocalizedText.of("job.unexpectedError");

    private final ExecutorService jobExecutor;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final JobProperties properties;
    private final Map<UUID, RunningJob> running = new ConcurrentHashMap<>();
    private final Deque<JobSnapshot> history = new ArrayDeque<>();
    private UUID exclusiveJob;

    /**
     * Starts a job in the background.
     *
     * @throws JobAlreadyRunningException if a job is already running for the key
     */
    public synchronized JobSnapshot submit(String key, LocalizedText title, JobTask<?> task) {
        rejectWhileExclusiveJobRuns();
        findRunning(key).ifPresent(job -> {
            throw new JobAlreadyRunningException(job.snapshot());
        });
        UUID id = UUID.randomUUID();
        RunningJob job = new RunningJob(JobSnapshot.started(id, key, title, clock.instant()), this::publish, clock,
                properties.progressInterval());
        running.put(id, job);
        job.publishStart();
        jobExecutor.execute(() -> execute(job, task));
        log.info("Job '{}' started ({})", Messages.english(title), id);
        return job.snapshot();
    }

    /**
     * Transitional overload until every caller passes a translatable title.
     *
     * @deprecated use {@link #submit(String, LocalizedText, JobTask)}
     */
    @Deprecated(forRemoval = true)
    public JobSnapshot submit(String key, String title, JobTask<?> task) {
        return submit(key, LocalizedText.literal(title), task);
    }

    /**
     * Transitional overload until every caller passes a translatable title.
     *
     * @deprecated use {@link #submitExclusive(String, LocalizedText, JobTask)}
     */
    @Deprecated(forRemoval = true)
    public JobSnapshot submitExclusive(String key, String title, JobTask<?> task) {
        return submitExclusive(key, LocalizedText.literal(title), task);
    }

    /**
     * Starts a job that runs alone: it only starts if no job is running, and no other job starts until it ends.
     *
     * @throws JobAlreadyRunningException if a job is running
     */
    public synchronized JobSnapshot submitExclusive(String key, LocalizedText title, JobTask<?> task) {
        rejectWhileAnyJobRuns();
        JobSnapshot started = submit(key, title, task);
        exclusiveJob = started.id();
        return started;
    }

    /**
     * Runs an action if no job is running. No job starts until it is done.
     *
     * @throws JobAlreadyRunningException if a job is running
     */
    public synchronized void runWhileIdle(Runnable action) {
        rejectWhileAnyJobRuns();
        action.run();
    }

    /** Running jobs first, then the finished ones, newest on top in each group. */
    public synchronized List<JobSnapshot> findAll() {
        Stream<JobSnapshot> active = running.values().stream()
                .map(RunningJob::snapshot)
                .sorted(Comparator.comparing(JobSnapshot::startedAt).reversed());
        return Stream.concat(active, history.stream()).toList();
    }

    public synchronized JobSnapshot find(UUID jobId) {
        RunningJob job = running.get(jobId);
        if (job != null) {
            return job.snapshot();
        }
        return history.stream()
                .filter(snapshot -> snapshot.id().equals(jobId))
                .findFirst()
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    /**
     * Requests cancellation. The job ends the next time it checks for cancellation; a job that has already finished
     * stays unchanged.
     */
    public JobSnapshot cancel(UUID jobId) {
        RunningJob job = running.get(jobId);
        if (job == null) {
            return find(jobId);
        }
        job.requestCancellation();
        return job.snapshot();
    }

    @PreDestroy
    void cancelAllOnShutdown() {
        running.values().forEach(RunningJob::requestCancellation);
    }

    private void execute(RunningJob job, JobTask<?> task) {
        JobSnapshot finished;
        try {
            finished = job.succeed(task.run(job));
        } catch (JobCancelledException e) {
            finished = job.cancel();
        } catch (RuntimeException e) {
            log.warn("Job '{}' failed", Messages.english(job.snapshot().title()), e);
            finished = job.fail(errorMessage(e));
        }
        archive(job, finished);
        log.info("Job '{}' finished with {}", Messages.english(finished.title()), finished.status());
    }

    private synchronized void archive(RunningJob job, JobSnapshot finished) {
        running.remove(finished.id(), job);
        if (finished.id().equals(exclusiveJob)) {
            exclusiveJob = null;
        }
        history.addFirst(finished);
        while (history.size() > properties.historySize()) {
            history.removeLast();
        }
    }

    private void rejectWhileAnyJobRuns() {
        running.values().stream().findFirst().ifPresent(job -> {
            throw new JobAlreadyRunningException(job.snapshot());
        });
    }

    private void rejectWhileExclusiveJobRuns() {
        if (exclusiveJob != null && running.containsKey(exclusiveJob)) {
            throw new JobAlreadyRunningException(running.get(exclusiveJob).snapshot());
        }
    }

    private Optional<RunningJob> findRunning(String key) {
        return running.values().stream()
                .filter(job -> job.snapshot().key().equals(key))
                .findFirst();
    }

    private void publish(JobSnapshot snapshot) {
        events.publishEvent(new JobChangedEvent(snapshot));
    }

    /**
     * Business errors bring a translatable description; the technical message of a problem would only be the HTTP
     * status. Other messages are shown as they are.
     */
    private static LocalizedText errorMessage(RuntimeException exception) {
        if (exception instanceof Localized localized) {
            return localized.localizedText();
        }
        String message = exception instanceof ErrorResponseException problem && problem.getBody().getDetail() != null
                ? problem.getBody().getDetail()
                : exception.getMessage();
        return message == null || message.isBlank() ? UNEXPECTED_ERROR : LocalizedText.literal(message);
    }
}
