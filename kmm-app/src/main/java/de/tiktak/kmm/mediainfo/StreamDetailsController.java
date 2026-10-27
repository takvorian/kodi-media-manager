package de.tiktak.kmm.mediainfo;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.nfo.StreamDetails;
import lombok.RequiredArgsConstructor;

/**
 * Liest technische Angaben mit ffprobe aus, für eine ganze Quelle oder einen einzelnen Film bzw. eine Episode.
 */
@RestController
@RequestMapping(StreamDetailsController.BASE_PATH)
@RequiredArgsConstructor
public class StreamDetailsController {

    public static final String BASE_PATH = "/api/v1";

    private final StreamDetailsService service;

    /** Startet das Auslesen im Hintergrund. Fortschritt und Ergebnis liefert die Job-API. */
    @PostMapping("/sources/{sourceId}/stream-details")
    public ResponseEntity<JobSnapshot> probeSource(@PathVariable long sourceId,
            @RequestParam(defaultValue = "MISSING") StreamDetailsScope scope) {
        JobSnapshot job = service.startSource(sourceId, scope);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @PostMapping("/movies/{movieId}/stream-details")
    public StreamDetails probeMovie(@PathVariable long movieId) {
        return service.probeMovie(movieId);
    }

    @PostMapping("/episodes/{episodeId}/stream-details")
    public StreamDetails probeEpisode(@PathVariable long episodeId) {
        return service.probeEpisode(episodeId);
    }
}
