package de.tiktak.kmm.tvshow;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Changes the language of TV shows as a batch edit job and, on request, fetches the texts of the TV show and its
 * episodes in the new language. Nothing is renamed.
 */
@Service
@RequiredArgsConstructor
public class TvShowLanguageService {

    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final TvShowBatchService batchService;

    public JobSnapshot start(TvShowLanguageChange change) {
        return batchService.startFill(LocalizedText.of("tvShow.batch.languageJobTitle"), change.showIds(),
                showId -> apply(showId, change));
    }

    /** Report only after the transaction has ended: the other episodes are saved by then. */
    private boolean apply(long showId, TvShowLanguageChange change) {
        boolean changed = metadataService.changeLanguage(showId, change.language());
        if (!change.refetchTexts()) {
            return changed;
        }
        TvShowScrapeService.TextRefetch refetch = scrapeService.refetchTexts(showId);
        if (!refetch.failedFiles().isEmpty()) {
            throw TvShowProblemException.nfosNotWritten(refetch.failedFiles());
        }
        return refetch.changed() || changed;
    }
}
