package de.tiktak.kmm.tvshow;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Ändert die Sprache von Serien als Job der Stapel-Bearbeitung und holt auf Wunsch die Texte der Serie und ihrer
 * Episoden in der neuen Sprache. Umbenannt wird dabei nicht.
 */
@Service
@RequiredArgsConstructor
public class TvShowLanguageService {

    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final TvShowBatchService batchService;

    public JobSnapshot start(TvShowLanguageChange change) {
        List<Long> showIds = change.showIds();
        String title = showIds.size() == 1 ? "Sprache von 1 Serie ändern" : "Sprache von %d Serien ändern";
        return batchService.startFill(title, showIds, showId -> apply(showId, change));
    }

    /** Erst nach dem Ende der Transaktion melden: Die übrigen Episoden sind dann schon gespeichert. */
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
