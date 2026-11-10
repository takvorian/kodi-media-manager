package de.tiktak.kmm.tvshow;

import java.net.URI;
import java.util.List;
import java.util.OptionalInt;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Scanning the folder of single TV shows or seasons in KMM; every scan runs as a job.
 */
@RestController
@RequiredArgsConstructor
public class TvShowFolderScanController {

    public static final String SHOWS_SCAN_PATH = TvShowController.SHOWS_PATH + "/scan";
    public static final String SHOW_SCAN_PATH = TvShowController.SHOW_PATH + "/scan";
    public static final String SEASON_SCAN_PATH = TvShowController.SHOW_PATH + "/seasons/{season}/scan";

    private final TvShowFolderScanService scanService;

    @PostMapping(SHOWS_SCAN_PATH)
    public ResponseEntity<JobSnapshot> scanShows(@Valid @RequestBody Shows shows) {
        return accepted(scanService.startScans(shows.showIds()));
    }

    @PostMapping(SHOW_SCAN_PATH)
    public ResponseEntity<JobSnapshot> scanShow(@PathVariable long showId) {
        return accepted(scanService.startScan(showId, OptionalInt.empty()));
    }

    @PostMapping(SEASON_SCAN_PATH)
    public ResponseEntity<JobSnapshot> scanSeason(@PathVariable long showId,
                                                  @PathVariable @PositiveOrZero int season) {
        return accepted(scanService.startScan(showId, OptionalInt.of(season)));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Shows(@NotNull @NotEmpty @Size(max = TvShowTrailerController.MAX_SHOWS) List<@NotNull Long> showIds) {
    }
}
