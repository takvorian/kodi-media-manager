package de.tiktak.kmm.movie;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.plugin.ScraperChange;
import de.tiktak.kmm.plugin.ScraperChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MovieScraperController {

    public static final String SCRAPER_PATH = MovieController.MOVIE_PATH + "/scraper";

    private final MovieScrapeService scrapeService;
    private final MovieScraperService scraperService;

    /** The movie's metadata scraper and the ones it can be switched to. */
    @GetMapping(SCRAPER_PATH)
    public ScraperChoice scraper(@PathVariable long movieId) {
        return scrapeService.scraperChoice(movieId);
    }

    /** Chooses the movie's metadata scraper as a job. */
    @PutMapping(SCRAPER_PATH)
    public ResponseEntity<JobSnapshot> change(@PathVariable long movieId, @Valid @RequestBody ScraperChange change) {
        JobSnapshot job = scraperService.start(movieId, change);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    /** After a match from the online search was saved: keeps its scraper if it is not the one of the source. */
    @PostMapping(SCRAPER_PATH + "/adopted")
    public ScraperChoice adopted(@PathVariable long movieId,
            @RequestParam @Pattern(regexp = "[a-z0-9][a-z0-9_.-]{0,39}") String scraper) {
        scrapeService.rememberScraper(movieId, scraper);
        return scrapeService.scraperChoice(movieId);
    }
}
