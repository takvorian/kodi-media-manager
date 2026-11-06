package de.tiktak.kmm.movie;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Changes the language of movies as a batch edit job and, if wanted, fetches their texts in the new language.
 * Nothing is renamed.
 */
@Service
@RequiredArgsConstructor
public class MovieLanguageService {

    private final MovieMetadataService metadataService;
    private final MovieScrapeService scrapeService;
    private final MovieBatchService batchService;

    public JobSnapshot start(MovieLanguageChange change) {
        return batchService.startFill(LocalizedText.of("movie.batch.language.jobTitle"), change.movieIds(),
                movieId -> apply(movieId, change));
    }

    private boolean apply(long movieId, MovieLanguageChange change) {
        boolean changed = metadataService.changeLanguage(movieId, change.language());
        return change.refetchTexts() && scrapeService.refetchTexts(movieId) || changed;
    }
}
