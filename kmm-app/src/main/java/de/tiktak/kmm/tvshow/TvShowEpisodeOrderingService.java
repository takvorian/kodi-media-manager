package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Changes the episode order of a TV show as a job and, on request, fetches titles and texts of all its episodes again
 * in the new order. The files stay as they are; they must be numbered in the chosen order.
 */
@Service
@RequiredArgsConstructor
public class TvShowEpisodeOrderingService {

    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final TvShowBatchService batchService;

    public JobSnapshot start(long showId, TvShowEpisodeOrderingChange change) {
        return batchService.startFill(LocalizedText.of("tvShow.batch.episodeOrderingJobTitle"), List.of(showId),
                id -> apply(id, change));
    }

    private boolean apply(long showId, TvShowEpisodeOrderingChange change) {
        TvEpisodeOrdering ordering = change.resetsToSettings() ? null
                : scrapeService.requireEpisodeOrder(showId, change.scraperId(), change.orderId());
        boolean changed = metadataService.changeEpisodeOrdering(showId, ordering);
        if (!change.refetch()) {
            return changed;
        }
        TvShowAutoScrapeOutcome refetched = scrapeService.refetch(showId, EpisodeScope.all());
        if (!refetched.failedFiles().isEmpty()) {
            throw TvShowProblemException.nfosNotWritten(refetched.failedFiles());
        }
        return changed || refetched.episodesUpdated() > 0;
    }
}
