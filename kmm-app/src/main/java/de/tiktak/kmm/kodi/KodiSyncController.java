package de.tiktak.kmm.kodi;

import java.net.URI;
import java.util.List;
import java.util.OptionalInt;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
 * Manual sync with all Kodi devices, independent of the realtime sync.
 */
@RestController
@RequestMapping(KodiSyncController.BASE_PATH)
@RequiredArgsConstructor
public class KodiSyncController {

    public static final String BASE_PATH = "/api/v1/kodi";
    private static final int MAX_ENTRIES = 20_000;

    private final KodiSyncService syncService;
    private final KodiBatchRefreshService batchRefreshService;

    @PostMapping("/movies/{movieId}/refresh")
    public List<KodiSyncResult> refreshMovie(@PathVariable long movieId) {
        return syncService.refreshMovie(movieId);
    }

    /** Updates the selected movies as a job. */
    @PostMapping("/movies/refresh")
    public ResponseEntity<JobSnapshot> refreshMovies(@Valid @RequestBody Movies movies) {
        return accepted(batchRefreshService.startMovies(movies.movieIds()));
    }

    /** Updates the selected TV shows with their episodes as a job. */
    @PostMapping("/tvshows/refresh")
    public ResponseEntity<JobSnapshot> refreshTvShows(@Valid @RequestBody TvShows shows) {
        return accepted(batchRefreshService.startTvShows(shows.showIds()));
    }

    /** Updates the TV show with all its episodes as a job; the job result are the results of all devices. */
    @PostMapping("/tvshows/{showId}/refresh")
    public ResponseEntity<JobSnapshot> refreshTvShow(@PathVariable long showId) {
        return accepted(batchRefreshService.startTvShow(showId, OptionalInt.empty()));
    }

    /** Updates the episodes of a season as a job; the job result are the results of all devices. */
    @PostMapping("/tvshows/{showId}/seasons/{season}/refresh")
    public ResponseEntity<JobSnapshot> refreshSeason(@PathVariable long showId,
                                                     @PathVariable @PositiveOrZero int season) {
        return accepted(batchRefreshService.startTvShow(showId, OptionalInt.of(season)));
    }

    /** Selected TV shows: Kodi scans their folders for new episodes and cleans them up, as a job. */
    @PostMapping("/tvshows/scan")
    public ResponseEntity<JobSnapshot> scanTvShows(@Valid @RequestBody TvShows shows) {
        return accepted(batchRefreshService.startTvShowScans(shows.showIds()));
    }

    /** Kodi scans the TV show folder for new episodes and cleans it up, as a job. */
    @PostMapping("/tvshows/{showId}/scan")
    public ResponseEntity<JobSnapshot> scanTvShow(@PathVariable long showId) {
        return accepted(batchRefreshService.startTvShowScan(showId, OptionalInt.empty()));
    }

    /** Kodi scans the season folder, or the TV show folder, for new episodes and cleans it up, as a job. */
    @PostMapping("/tvshows/{showId}/seasons/{season}/scan")
    public ResponseEntity<JobSnapshot> scanSeason(@PathVariable long showId,
                                                  @PathVariable @PositiveOrZero int season) {
        return accepted(batchRefreshService.startTvShowScan(showId, OptionalInt.of(season)));
    }

    @PostMapping("/episodes/{episodeId}/refresh")
    public List<KodiSyncResult> refreshEpisode(@PathVariable long episodeId) {
        return syncService.refreshEpisode(episodeId);
    }

    @PostMapping("/sources/{sourceId}/scan")
    public List<KodiSyncResult> scanSource(@PathVariable long sourceId) {
        return syncService.scanSource(sourceId);
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Movies(@NotNull @NotEmpty @Size(max = MAX_ENTRIES) List<@NotNull Long> movieIds) {
    }

    public record TvShows(@NotNull @NotEmpty @Size(max = MAX_ENTRIES) List<@NotNull Long> showIds) {
    }
}
