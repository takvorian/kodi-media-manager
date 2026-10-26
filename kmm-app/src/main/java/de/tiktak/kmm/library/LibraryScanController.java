package de.tiktak.kmm.library;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieScanService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.TvShowScanService;
import lombok.RequiredArgsConstructor;

/**
 * Startet den Scan einer Quelle, je nach Inhalt als Film- oder Serien-Scan.
 */
@RestController
@RequiredArgsConstructor
public class LibraryScanController {

    public static final String SCAN_PATH = "/api/v1/sources/{sourceId}/scan";

    private final MediaSourceService sourceService;
    private final MovieScanService movieScanService;
    private final TvShowScanService tvShowScanService;

    /**
     * Startet den Scan im Hintergrund. Fortschritt und Ergebnis liefert die Job-API.
     */
    @PostMapping(SCAN_PATH)
    public ResponseEntity<JobSnapshot> scan(@PathVariable long sourceId) {
        MediaType mediaType = sourceService.require(sourceId).getMediaType();
        JobSnapshot job = mediaType == MediaType.TV_SHOW
                ? tvShowScanService.startScan(sourceId)
                : movieScanService.startScan(sourceId);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
