package de.tiktak.kmm.job;

import java.time.Instant;
import java.util.UUID;

/**
 * Unveränderlicher Zustand eines Jobs zu einem Zeitpunkt. Wird über REST und SSE ausgeliefert.
 *
 * @param key        fachlicher Schlüssel; pro Schlüssel läuft höchstens ein Job (z. B. {@code scan:3})
 * @param title      Beschreibung für die Oberfläche
 * @param done       erledigte Arbeitsschritte
 * @param total      Gesamtzahl der Schritte oder 0, solange sie unbekannt ist
 * @param message    aktueller Arbeitsschritt bzw. Fehlermeldung
 * @param result     Ergebnis eines erfolgreichen Jobs oder {@code null}
 * @param finishedAt Ende oder {@code null}, solange der Job läuft
 */
public record JobSnapshot(
        UUID id,
        String key,
        String title,
        JobStatus status,
        int done,
        int total,
        String message,
        Object result,
        Instant startedAt,
        Instant finishedAt) {

    static JobSnapshot started(UUID id, String key, String title, Instant startedAt) {
        return new JobSnapshot(id, key, title, JobStatus.RUNNING, 0, 0, null, null, startedAt, null);
    }

    JobSnapshot withProgress(int newDone, int newTotal, String newMessage) {
        return new JobSnapshot(id, key, title, status, newDone, newTotal, newMessage, result, startedAt, finishedAt);
    }

    JobSnapshot succeeded(Object jobResult, Instant end) {
        return new JobSnapshot(id, key, title, JobStatus.SUCCEEDED, total, total, null, jobResult, startedAt, end);
    }

    JobSnapshot failed(String error, Instant end) {
        return new JobSnapshot(id, key, title, JobStatus.FAILED, done, total, error, null, startedAt, end);
    }

    JobSnapshot cancelled(Instant end) {
        return new JobSnapshot(id, key, title, JobStatus.CANCELLED, done, total, null, null, startedAt, end);
    }
}
