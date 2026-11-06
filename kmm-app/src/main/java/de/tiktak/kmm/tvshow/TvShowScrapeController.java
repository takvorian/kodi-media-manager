package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Online search for TV shows. Search and proposal change nothing; the TV show is applied via
 * {@code PUT /api/v1/tvshows/{id}/metadata}, the episodes afterwards via
 * {@link #EPISODES_PATH}.
 */
@RestController
@RequiredArgsConstructor
public class TvShowScrapeController {

    public static final String SEARCH_PATH = TvShowController.SHOW_PATH + "/scraper-search";
    public static final String PROPOSAL_PATH = TvShowController.SHOW_PATH + "/scraper-proposal";
    public static final String EPISODES_PATH = TvShowController.SHOW_PATH + "/scraper-episodes";
    public static final String MISSING_EPISODES_PATH = TvShowController.SHOW_PATH + "/missing-episodes";

    private final TvShowScrapeService scrapeService;

    @GetMapping(SEARCH_PATH)
    public List<TvShowScrapeCandidate> search(
            @PathVariable long showId,
            @RequestParam String scraper,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer year) {
        return scrapeService.search(showId, scraper, title, year);
    }

    @GetMapping(PROPOSAL_PATH)
    public TvShowProposal proposal(@PathVariable long showId, @RequestParam String scraper,
            @RequestParam String providerId) {
        return scrapeService.proposal(showId, scraper, providerId);
    }

    /** Fills empty details of the episodes and writes their NFOs. */
    @PostMapping(EPISODES_PATH)
    public EpisodeFillResult fillEpisodes(@PathVariable long showId, @RequestParam String scraper,
            @RequestParam String providerId) {
        return scrapeService.fillFromProvider(showId, scraper, providerId);
    }

    @GetMapping(MISSING_EPISODES_PATH)
    public List<MissingEpisode> missingEpisodes(@PathVariable long showId, @RequestParam String scraper) {
        return scrapeService.missingEpisodes(showId, scraper);
    }
}
