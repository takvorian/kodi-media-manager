package de.tiktak.kmm.job;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * Verteilt Job-Änderungen per Server-Sent Events an alle verbundenen Oberflächen. Verbindungen,
 * an die nicht mehr gesendet werden kann, werden entfernt.
 */
@Slf4j
@Component
class JobEventBroadcaster {

    static final String JOB_EVENT = "job";
    static final String HEARTBEAT_COMMENT = "heartbeat";
    /** Kein Server-Timeout: Der Browser hält die Verbindung und baut sie bei Abbruch selbst neu auf. */
    private static final long NO_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Öffnet eine neue Verbindung und sendet zuerst den aktuellen Stand aller bekannten Jobs.
     */
    SseEmitter connect(List<JobSnapshot> currentJobs) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        emitters.add(emitter);
        currentJobs.forEach(job -> send(emitter, job));
        return emitter;
    }

    @EventListener
    void onJobChanged(JobChangedEvent event) {
        emitters.forEach(emitter -> send(emitter, event.job()));
    }

    /** Hält Verbindungen über Proxys hinweg offen und erkennt getrennte Clients. */
    @Scheduled(fixedRateString = "${kmm.jobs.heartbeat-interval:30s}")
    void sendHeartbeat() {
        emitters.forEach(emitter -> sendSafely(emitter, SseEmitter.event().comment(HEARTBEAT_COMMENT)));
    }

    int connectionCount() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, JobSnapshot job) {
        sendSafely(emitter, SseEmitter.event().name(JOB_EVENT).id(job.id().toString()).data(job));
    }

    private void sendSafely(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping SSE connection: {}", e.getMessage());
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }
}
