package de.tiktak.kmm.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class JobEventBroadcasterTest {

    private static final JobSnapshot JOB = JobSnapshot.started(UUID.randomUUID(), "scan:1", "Scan",
            Instant.parse("2026-10-25T12:00:00Z"));

    private final JobEventBroadcaster broadcaster = new JobEventBroadcaster();

    @Test
    void registersConnectionsAndSendsCurrentJobs() {
        SseEmitter emitter = broadcaster.connect(List.of(JOB));

        assertThat(emitter.getTimeout()).isZero();
        assertThat(broadcaster.connectionCount()).isEqualTo(1);
    }

    @Test
    void sendsJobChangesAndHeartbeatsToOpenConnections() {
        broadcaster.connect(List.of());
        broadcaster.connect(List.of());

        broadcaster.onJobChanged(new JobChangedEvent(JOB));
        broadcaster.sendHeartbeat();

        assertThat(broadcaster.connectionCount()).isEqualTo(2);
    }

    @Test
    void dropsConnectionsThatCanNoLongerReceive() {
        SseEmitter closed = broadcaster.connect(List.of());
        broadcaster.connect(List.of());
        closed.complete();

        broadcaster.onJobChanged(new JobChangedEvent(JOB));

        assertThat(broadcaster.connectionCount()).isEqualTo(1);
    }

    @Test
    void dropsClosedConnectionOnHeartbeat() {
        SseEmitter closed = broadcaster.connect(List.of());
        closed.complete();

        broadcaster.sendHeartbeat();

        assertThat(broadcaster.connectionCount()).isZero();
    }
}
