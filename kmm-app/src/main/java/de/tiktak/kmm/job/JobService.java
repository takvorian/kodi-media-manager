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

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Startet Hintergrund-Jobs und hält ihren Zustand. Laufende Jobs und die letzten beendeten Jobs
 * werden im Speicher gehalten; nach einem Neustart beginnt die Liste leer. Pro fachlichem
 * Schlüssel läuft höchstens ein Job gleichzeitig.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final String UNEXPECTED_ERROR = "Unerwarteter Fehler";

    private final ExecutorService jobExecutor;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final JobProperties properties;
    private final Map<UUID, RunningJob> running = new ConcurrentHashMap<>();
    private final Deque<JobSnapshot> history = new ArrayDeque<>();

    /**
     * Startet einen Job im Hintergrund.
     *
     * @throws JobAlreadyRunningException wenn für den Schlüssel bereits ein Job läuft
     */
    public synchronized JobSnapshot submit(String key, String title, JobTask<?> task) {
        findRunning(key).ifPresent(job -> {
            throw new JobAlreadyRunningException(job.snapshot());
        });
        UUID id = UUID.randomUUID();
        RunningJob job = new RunningJob(JobSnapshot.started(id, key, title, clock.instant()), this::publish, clock,
                properties.progressInterval());
        running.put(id, job);
        job.publishStart();
        jobExecutor.execute(() -> execute(job, task));
        log.info("Job '{}' started ({})", title, id);
        return job.snapshot();
    }

    /** Laufende Jobs zuerst, danach die beendeten, jeweils die neuesten oben. */
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
     * Fordert den Abbruch an. Der Job endet, sobald er das nächste Mal auf Abbruch prüft; ein bereits
     * beendeter Job bleibt unverändert.
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
            log.warn("Job '{}' failed", job.snapshot().title(), e);
            finished = job.fail(errorMessage(e));
        }
        archive(job, finished);
        log.info("Job '{}' finished with {}", finished.title(), finished.status());
    }

    private synchronized void archive(RunningJob job, JobSnapshot finished) {
        running.remove(finished.id(), job);
        history.addFirst(finished);
        while (history.size() > properties.historySize()) {
            history.removeLast();
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

    private static String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? UNEXPECTED_ERROR : message;
    }
}
