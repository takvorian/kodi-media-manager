package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Ändert die Sprache von Filmen als Job der Stapel-Bearbeitung und holt auf Wunsch ihre Texte in der neuen Sprache.
 * Umbenannt wird dabei nicht.
 */
@Service
@RequiredArgsConstructor
public class MovieLanguageService {

    private final MovieMetadataService metadataService;
    private final MovieScrapeService scrapeService;
    private final MovieBatchService batchService;

    public JobSnapshot start(MovieLanguageChange change) {
        List<Long> movieIds = change.movieIds();
        String title = movieIds.size() == 1 ? "Sprache von 1 Film ändern" : "Sprache von %d Filmen ändern";
        return batchService.startFill(title, movieIds, movieId -> apply(movieId, change));
    }

    private boolean apply(long movieId, MovieLanguageChange change) {
        boolean changed = metadataService.changeLanguage(movieId, change.language());
        return change.refetchTexts() && scrapeService.refetchTexts(movieId) || changed;
    }
}
