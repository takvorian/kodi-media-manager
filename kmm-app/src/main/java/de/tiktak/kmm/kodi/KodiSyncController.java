package de.tiktak.kmm.kodi;

import java.net.URI;
import java.util.List;

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

    /** Updates the selected TV shows without their episodes as a job. */
    @PostMapping("/tvshows/refresh")
    public ResponseEntity<JobSnapshot> refreshTvShows(@Valid @RequestBody TvShows shows) {
        return accepted(batchRefreshService.startTvShows(shows.showIds()));
    }

    @PostMapping("/tvshows/{showId}/refresh")
    public List<KodiSyncResult> refreshTvShow(@PathVariable long showId) {
        return syncService.refreshTvShow(showId);
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
