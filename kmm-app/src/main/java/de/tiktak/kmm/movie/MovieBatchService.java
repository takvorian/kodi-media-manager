package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stapel-Bearbeitung: gemeinsame Änderungen an vielen Filmen als Hintergrund-Job, je Film in einer eigenen
 * Transaktion. Ein Fehler bei einem Film hält die übrigen nicht auf. Umbenannt wird dabei nicht.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieBatchService {

    static final String JOB_KEY = "movies-batch";
    static final int MAX_LISTED_FAILURES = 20;

    private final MovieMetadataService metadataService;
    private final MovieTagService tagService;
    private final JobService jobService;

    public JobSnapshot start(MovieBatchEdit edit) {
        return jobService.submit(JOB_KEY, "%d Filme bearbeiten".formatted(edit.movieIds().size()),
                context -> apply(edit, context));
    }

    MovieBatchResult apply(MovieBatchEdit edit, JobContext context) {
        List<String> failures = new ArrayList<>();
        int changed = 0;
        int unchanged = 0;
        int failed = 0;
        List<Long> movieIds = edit.movieIds();
        for (long movieId : movieIds) {
            context.throwIfCancellationRequested();
            context.reportProgress(changed + unchanged + failed, movieIds.size(), "Film " + movieId);
            try {
                if (change(movieId, edit)) {
                    changed++;
                } else {
                    unchanged++;
                }
            } catch (ErrorResponseException e) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add("Film %d: %s".formatted(movieId, e.getBody().getDetail()));
                }
            }
        }
        context.reportProgress(movieIds.size(), movieIds.size(), null);
        log.info("Batch edit: {} changed, {} unchanged, {} failed", changed, unchanged, failed);
        return new MovieBatchResult(changed, unchanged, failed, failures);
    }

    private boolean change(long movieId, MovieBatchEdit edit) {
        boolean metadata = edit.changesMetadata() && metadataService.change(movieId, edit::apply);
        boolean tags = edit.tags().changes() && tagService.changeTags(movieId, edit.tags()::apply);
        return metadata || tags;
    }
}
