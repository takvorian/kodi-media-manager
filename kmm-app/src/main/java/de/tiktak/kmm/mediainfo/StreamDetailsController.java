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

    /**
     * Liest den Film nach der Online-Suche aus, wenn das eingestellt ist.
     *
     * @return die Angaben oder 204, wenn das Auslesen nach dem Scrapen abgeschaltet ist
     */
    @PostMapping("/movies/{movieId}/stream-details/after-scrape")
    public ResponseEntity<StreamDetails> probeMovieAfterScrape(@PathVariable long movieId) {
        return service.probeMovieIfEnabled(movieId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Liest nach der Online-Suche die Episoden der Serie ohne Angaben aus, wenn das eingestellt ist.
     *
     * @return das Ergebnis oder 204, wenn das Auslesen nach dem Scrapen abgeschaltet ist
     */
    @PostMapping("/tvshows/{showId}/stream-details/after-scrape")
    public ResponseEntity<ShowStreamDetailsResult> probeShowAfterScrape(@PathVariable long showId) {
        return service.probeShowIfEnabled(showId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
