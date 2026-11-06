package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.LongPredicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch edit: common changes to many movies as a background job, each movie in its own transaction. A failure with
 * one movie does not stop the others. Nothing is renamed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieBatchService {

    static final String JOB_KEY = "movies-batch";
    static final int MAX_LISTED_FAILURES = 20;
    /** Argument of the job title with the number of movies. */
    public static final String COUNT_ARGUMENT = "count";

    private final MovieMetadataService metadataService;
    private final MovieTagService tagService;
    private final MovieTrailerService trailerService;
    private final MovieEnrichmentService enrichmentService;
    private final JobService jobService;

    public JobSnapshot start(MovieBatchEdit edit) {
        LocalizedText title = LocalizedText.of("movie.batch.edit.jobTitle")
                .with(COUNT_ARGUMENT, edit.movieIds().size());
        return jobService.submit(JOB_KEY, title, context -> apply(edit, context));
    }

    /** Enters the best suggestion for movies without a trailer, as a job like the batch edit. */
    public JobSnapshot startTrailerFill(List<Long> movieIds) {
        return startFill(LocalizedText.of("movie.batch.trailerFill.jobTitle"), movieIds,
                trailerService::fillMissing);
    }

    /** Updates ratings and fills empty fields with the supplementing scrapers, as a job. */
    public JobSnapshot startEnrichment(List<Long> movieIds) {
        return startFill(LocalizedText.of("movie.batch.enrichment.jobTitle"), movieIds, enrichmentService::refresh);
    }

    /**
     * Changes every movie of the selection, as a job like the batch edit.
     *
     * @param title  title of the job; gets the number of distinct movies as argument {@value #COUNT_ARGUMENT}
     * @param change change per movie; returns whether something changed
     */
    public JobSnapshot startFill(LocalizedText title, List<Long> movieIds, LongPredicate change) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(movieIds));
        return jobService.submit(JOB_KEY, title.with(COUNT_ARGUMENT, distinct.size()),
                context -> apply(distinct, change, context));
    }

    MovieBatchResult apply(MovieBatchEdit edit, JobContext context) {
        return apply(edit.movieIds(), movieId -> change(movieId, edit), context);
    }

    MovieBatchResult apply(List<Long> movieIds, LongPredicate change, JobContext context) {
        List<LocalizedText> failures = new ArrayList<>();
        int changed = 0;
        int unchanged = 0;
        int failed = 0;
        for (long movieId : movieIds) {
            context.throwIfCancellationRequested();
            context.reportProgress(changed + unchanged + failed, movieIds.size(),
                    LocalizedText.of("movie.item").with("id", movieId));
            try {
                if (change.test(movieId)) {
                    changed++;
                } else {
                    unchanged++;
                }
            } catch (ErrorResponseException e) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add(LocalizedText.of("movie.failure")
                            .with("id", movieId)
                            .with("reason", LocalizedReasons.of(e)));
                }
            }
        }
        context.reportProgress(movieIds.size(), movieIds.size());
        log.info("Batch edit: {} changed, {} unchanged, {} failed", changed, unchanged, failed);
        return new MovieBatchResult(changed, unchanged, failed, failures);
    }

    private boolean change(long movieId, MovieBatchEdit edit) {
        boolean metadata = edit.changesMetadata() && metadataService.change(movieId, edit::apply);
        boolean tags = edit.tags().changes() && tagService.changeTags(movieId, edit.tags()::apply);
        return metadata || tags;
    }
}
