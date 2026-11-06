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
 * Reads technical details with ffprobe, for a whole source or a single movie or episode.
 */
@RestController
@RequestMapping(StreamDetailsController.BASE_PATH)
@RequiredArgsConstructor
public class StreamDetailsController {

    public static final String BASE_PATH = "/api/v1";

    private final StreamDetailsService service;

    /** Starts the reading in the background. Progress and result are provided by the job API. */
    @PostMapping("/sources/{sourceId}/stream-details")
    public ResponseEntity<JobSnapshot> probeSource(@PathVariable long sourceId,
            @RequestParam(defaultValue = "MISSING") StreamDetailsScope scope) {
        JobSnapshot job = service.startSource(sourceId, scope);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    /** Re-reads all episodes of a season in the background. Progress and result are provided by the job API. */
    @PostMapping("/tvshows/{showId}/seasons/{season}/stream-details")
    public ResponseEntity<JobSnapshot> probeSeason(@PathVariable long showId, @PathVariable int season) {
        JobSnapshot job = service.startSeason(showId, season);
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
     * Reads the movie after the online search if that is enabled.
     *
     * @return the details or 204 if reading after scraping is disabled
     */
    @PostMapping("/movies/{movieId}/stream-details/after-scrape")
    public ResponseEntity<StreamDetails> probeMovieAfterScrape(@PathVariable long movieId) {
        return service.probeMovieIfEnabled(movieId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * After the online search, reads the episodes of the TV show without details if that is enabled.
     *
     * @return the result or 204 if reading after scraping is disabled
     */
    @PostMapping("/tvshows/{showId}/stream-details/after-scrape")
    public ResponseEntity<ShowStreamDetailsResult> probeShowAfterScrape(@PathVariable long showId) {
        return service.probeShowIfEnabled(showId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
