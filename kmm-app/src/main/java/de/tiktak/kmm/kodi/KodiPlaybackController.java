package de.tiktak.kmm.kodi;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Holt den Gesehen-Status aus Kodi, für eine ganze Quelle oder einen einzelnen Film bzw. eine Episode.
 */
@RestController
@RequestMapping(KodiSyncController.BASE_PATH)
@RequiredArgsConstructor
public class KodiPlaybackController {

    private final KodiPlaybackService playbackService;

    /** Startet die Übernahme im Hintergrund. Fortschritt und Ergebnis liefert die Job-API. */
    @PostMapping("/sources/{sourceId}/playback")
    public ResponseEntity<JobSnapshot> importSource(@PathVariable long sourceId) {
        JobSnapshot job = playbackService.startSourceImport(sourceId);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @PostMapping("/movies/{movieId}/playback")
    public KodiPlaybackResult importMovie(@PathVariable long movieId) {
        return playbackService.importMovie(movieId);
    }

    @PostMapping("/episodes/{episodeId}/playback")
    public KodiPlaybackResult importEpisode(@PathVariable long episodeId) {
        return playbackService.importEpisode(episodeId);
    }
}
