package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.plugin.ScraperChange;
import lombok.RequiredArgsConstructor;

/**
 * Chooses the metadata scraper of a movie as a job and, on request, fetches its texts from it again.
 */
@Service
@RequiredArgsConstructor
public class MovieScraperService {

    private final MovieMetadataService metadataService;
    private final MovieScrapeService scrapeService;
    private final MovieBatchService batchService;

    public JobSnapshot start(long movieId, ScraperChange change) {
        return batchService.startFill(LocalizedText.of("movie.batch.scraper.jobTitle"), List.of(movieId),
                id -> apply(id, change));
    }

    private boolean apply(long movieId, ScraperChange change) {
        if (change.scraperId() != null) {
            scrapeService.requireScraper(change.scraperId());
        }
        boolean changed = metadataService.changeScraper(movieId, change.scraperId());
        return change.rescrape() && scrapeService.refetchTexts(movieId) || changed;
    }
}
