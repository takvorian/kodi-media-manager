package de.tiktak.kmm.tvshow;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TvShowEpisodeOrderingController {

    public static final String ORDERS_PATH = TvShowController.SHOW_PATH + "/episode-orders";
    public static final String ORDERING_PATH = TvShowController.SHOW_PATH + "/episode-ordering";

    private final TvShowScrapeService scrapeService;
    private final TvShowEpisodeOrderingService orderingService;

    /** The episode orders the providers know for the TV show; empty if it is not identified at any of them. */
    @GetMapping(ORDERS_PATH)
    public List<TvShowScrapeService.ScraperEpisodeOrders> orders(@PathVariable long showId) {
        return scrapeService.episodeOrders(showId);
    }

    /** Changes the episode order of the TV show as a job. */
    @PutMapping(ORDERING_PATH)
    public ResponseEntity<JobSnapshot> change(@PathVariable long showId,
            @Valid @RequestBody TvShowEpisodeOrderingChange change) {
        JobSnapshot job = orderingService.start(showId, change);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
