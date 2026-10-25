package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Online-Suche nach Metadaten für einen Film. Die Endpunkte ändern nichts; übernommen wird über
 * {@code PUT /api/v1/movies/{id}/metadata}.
 */
@RestController
@RequiredArgsConstructor
public class MovieScrapeController {

    public static final String SEARCH_PATH = MovieController.MOVIE_PATH + "/scraper-search";
    public static final String PROPOSAL_PATH = MovieController.MOVIE_PATH + "/scraper-proposal";

    private final MovieScrapeService scrapeService;

    @GetMapping(SEARCH_PATH)
    public List<ScrapeCandidate> search(
            @PathVariable long movieId,
            @RequestParam String scraper,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer year) {
        return scrapeService.search(movieId, scraper, title, year);
    }

    @GetMapping(PROPOSAL_PATH)
    public MovieMetadata proposal(
            @PathVariable long movieId,
            @RequestParam String scraper,
            @RequestParam String providerId) {
        return scrapeService.proposal(movieId, scraper, providerId);
    }
}
