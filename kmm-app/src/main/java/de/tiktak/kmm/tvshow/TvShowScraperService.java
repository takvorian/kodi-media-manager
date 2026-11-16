package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.plugin.ScraperChange;
import lombok.RequiredArgsConstructor;

/**
 * Chooses the metadata scraper of a TV show as a job and, on request, identifies it there again and fetches the
 * texts of the TV show and all its episodes from it. The files stay as they are.
 */
@Service
@RequiredArgsConstructor
public class TvShowScraperService {

    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final TvShowBatchService batchService;

    public JobSnapshot start(long showId, ScraperChange change) {
        return batchService.startFill(LocalizedText.of("tvShow.batch.scraperJobTitle"), List.of(showId),
                id -> apply(id, change));
    }

    private boolean apply(long showId, ScraperChange change) {
        if (change.scraperId() != null) {
            scrapeService.requireScraper(change.scraperId());
        }
        boolean changed = metadataService.changeScraper(showId, change.scraperId());
        if (!change.rescrape()) {
            return changed;
        }
        TvShowScrapeService.TextRefetch refetched = scrapeService.refetchTexts(showId);
        if (!refetched.failedFiles().isEmpty()) {
            throw TvShowProblemException.nfosNotWritten(refetched.failedFiles());
        }
        return changed || refetched.changed();
    }
}
