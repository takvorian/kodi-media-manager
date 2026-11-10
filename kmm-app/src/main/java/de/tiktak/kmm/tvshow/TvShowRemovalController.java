package de.tiktak.kmm.tvshow;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalRequest;
import lombok.RequiredArgsConstructor;

/**
 * Removing episodes, seasons and TV shows: {@code GET} previews, {@code POST} removes as a job.
 */
@RestController
@RequiredArgsConstructor
public class TvShowRemovalController {

    public static final String EPISODE_REMOVAL_PATH = TvShowController.EPISODE_PATH + "/removal";
    public static final String SEASON_REMOVAL_PATH = TvShowController.SHOW_PATH + "/seasons/{season}/removal";
    public static final String SHOW_REMOVAL_PATH = TvShowController.SHOW_PATH + "/removal";

    private final TvShowRemovalService removalService;

    @GetMapping(EPISODE_REMOVAL_PATH)
    public RemovalPlan planEpisode(@PathVariable long episodeId) {
        return removalService.planEpisode(episodeId);
    }

    @PostMapping(EPISODE_REMOVAL_PATH)
    public ResponseEntity<JobSnapshot> removeEpisode(@PathVariable long episodeId,
                                                     @RequestBody RemovalRequest request) {
        return accepted(removalService.removeEpisode(episodeId, request));
    }

    @GetMapping(SEASON_REMOVAL_PATH)
    public RemovalPlan planSeason(@PathVariable long showId, @PathVariable int season) {
        return removalService.planSeason(showId, season);
    }

    @PostMapping(SEASON_REMOVAL_PATH)
    public ResponseEntity<JobSnapshot> removeSeason(@PathVariable long showId, @PathVariable int season,
                                                    @RequestBody RemovalRequest request) {
        return accepted(removalService.removeSeason(showId, season, request));
    }

    @GetMapping(SHOW_REMOVAL_PATH)
    public RemovalPlan planShow(@PathVariable long showId) {
        return removalService.planShow(showId);
    }

    @PostMapping(SHOW_REMOVAL_PATH)
    public ResponseEntity<JobSnapshot> removeShow(@PathVariable long showId, @RequestBody RemovalRequest request) {
        return accepted(removalService.removeShow(showId, request));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
