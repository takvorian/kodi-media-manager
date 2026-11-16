package de.tiktak.kmm.tvshow;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.plugin.ScraperChange;
import de.tiktak.kmm.plugin.ScraperChoice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TvShowScraperController {

    public static final String SCRAPER_PATH = TvShowController.SHOW_PATH + "/scraper";

    private final TvShowScrapeService scrapeService;
    private final TvShowScraperService scraperService;

    /** The TV show's metadata scraper and the ones it can be switched to. */
    @GetMapping(SCRAPER_PATH)
    public ScraperChoice scraper(@PathVariable long showId) {
        return scrapeService.scraperChoice(showId);
    }

    /** Chooses the TV show's metadata scraper as a job. */
    @PutMapping(SCRAPER_PATH)
    public ResponseEntity<JobSnapshot> change(@PathVariable long showId, @Valid @RequestBody ScraperChange change) {
        JobSnapshot job = scraperService.start(showId, change);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
