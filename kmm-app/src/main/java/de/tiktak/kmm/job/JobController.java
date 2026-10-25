package de.tiktak.kmm.job;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(JobController.BASE_PATH)
@RequiredArgsConstructor
public class JobController {

    public static final String BASE_PATH = "/api/v1/jobs";
    public static final String EVENTS_PATH = BASE_PATH + "/events";

    private final JobService jobService;
    private final JobEventBroadcaster broadcaster;

    @GetMapping
    public List<JobSnapshot> findAll() {
        return jobService.findAll();
    }

    @GetMapping("/{jobId}")
    public JobSnapshot find(@PathVariable UUID jobId) {
        return jobService.find(jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public JobSnapshot cancel(@PathVariable UUID jobId) {
        return jobService.cancel(jobId);
    }

    /**
     * Server-Sent Events: Ereignis {@code job} mit dem aktuellen {@link JobSnapshot} bei jeder Änderung.
     */
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return broadcaster.connect(jobService.findAll());
    }
}
