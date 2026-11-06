package de.tiktak.kmm.job;

import java.time.Instant;
import java.util.UUID;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Immutable state of a job at one point in time. Delivered via REST and SSE.
 *
 * @param key        business key; at most one job runs per key (e.g. {@code scan:3})
 * @param title      description for the user interface
 * @param done       completed steps
 * @param total      total number of steps, or 0 while unknown
 * @param message    current step or error message
 * @param result     result of a successful job, or {@code null}
 * @param finishedAt end, or {@code null} while the job is running
 */
public record JobSnapshot(
        UUID id,
        String key,
        LocalizedText title,
        JobStatus status,
        int done,
        int total,
        LocalizedText message,
        Object result,
        Instant startedAt,
        Instant finishedAt) {

    static JobSnapshot started(UUID id, String key, LocalizedText title, Instant startedAt) {
        return new JobSnapshot(id, key, title, JobStatus.RUNNING, 0, 0, null, null, startedAt, null);
    }

    JobSnapshot withProgress(int newDone, int newTotal, LocalizedText newMessage) {
        return new JobSnapshot(id, key, title, status, newDone, newTotal, newMessage, result, startedAt, finishedAt);
    }

    JobSnapshot succeeded(Object jobResult, Instant end) {
        return new JobSnapshot(id, key, title, JobStatus.SUCCEEDED, total, total, null, jobResult, startedAt, end);
    }

    JobSnapshot failed(LocalizedText error, Instant end) {
        return new JobSnapshot(id, key, title, JobStatus.FAILED, done, total, error, null, startedAt, end);
    }

    JobSnapshot cancelled(Instant end) {
        return new JobSnapshot(id, key, title, JobStatus.CANCELLED, done, total, null, null, startedAt, end);
    }
}
