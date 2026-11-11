package de.tiktak.kmm.movie;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Scanning the folder of single movies in KMM; every scan runs as a job.
 */
@RestController
@RequiredArgsConstructor
public class MovieFolderScanController {

    public static final String MOVIES_SCAN_PATH = MovieController.MOVIES_PATH + "/scan";
    public static final String MOVIE_SCAN_PATH = MovieController.MOVIE_PATH + "/scan";

    private final MovieFolderScanService scanService;

    @PostMapping(MOVIES_SCAN_PATH)
    public ResponseEntity<JobSnapshot> scanMovies(@Valid @RequestBody MovieTrailerController.Movies movies) {
        return accepted(scanService.startScans(movies.movieIds()));
    }

    @PostMapping(MOVIE_SCAN_PATH)
    public ResponseEntity<JobSnapshot> scanMovie(@PathVariable long movieId) {
        return accepted(scanService.startScan(movieId));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
