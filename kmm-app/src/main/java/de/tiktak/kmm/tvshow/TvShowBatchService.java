package de.tiktak.kmm.tvshow;

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
import de.tiktak.kmm.web.LocalizedTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch editing of TV shows as a background job, each TV show in its own transaction. An error with one TV show does
 * not stop the others. Nothing is renamed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowBatchService {

    static final String JOB_KEY = "tvshows-batch";
    static final int MAX_LISTED_FAILURES = 20;
    /** Argument of the job title with the number of TV shows. */
    public static final String COUNT_ARGUMENT = "count";

    private final TvShowMetadataService metadataService;
    private final TvShowTrailerService trailerService;
    private final TvShowEnrichmentService enrichmentService;
    private final JobService jobService;

    public JobSnapshot start(TvShowBatchEdit edit) {
        return jobService.submit(JOB_KEY, LocalizedText.of("tvShow.batch.editJobTitle")
                .with(COUNT_ARGUMENT, edit.showIds().size()), context -> apply(edit, context));
    }

    /** Enters the best suggestion for TV shows without a trailer, as a job like the batch edit. */
    public JobSnapshot startTrailerFill(List<Long> showIds) {
        return startFill(LocalizedText.of("tvShow.batch.trailerFillJobTitle"), showIds, trailerService::fillMissing);
    }

    /** Updates ratings and fills empty fields via the enrichment scrapers, as a job. */
    public JobSnapshot startEnrichment(List<Long> showIds) {
        return startFill(LocalizedText.of("tvShow.batch.enrichmentJobTitle"), showIds, enrichmentService::refresh);
    }

    /**
     * Changes every TV show of the selection as a job like the batch edit.
     *
     * @param title  title of the job; it gets the number of TV shows as argument {@value #COUNT_ARGUMENT}
     * @param change change per TV show; returns whether something changed
     */
    public JobSnapshot startFill(LocalizedText title, List<Long> showIds, LongPredicate change) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(showIds));
        return jobService.submit(JOB_KEY, title.with(COUNT_ARGUMENT, distinct.size()),
                context -> apply(distinct, change, context));
    }


    TvShowBatchResult apply(TvShowBatchEdit edit, JobContext context) {
        return apply(edit.showIds(), showId -> metadataService.changeShow(showId, edit::apply), context);
    }

    TvShowBatchResult apply(List<Long> showIds, LongPredicate change, JobContext context) {
        List<LocalizedText> failures = new ArrayList<>();
        int changed = 0;
        int unchanged = 0;
        int failed = 0;
        for (long showId : showIds) {
            context.throwIfCancellationRequested();
            context.reportProgress(changed + unchanged + failed, showIds.size(),
                    LocalizedText.of("tvShow.batch.progress").with("id", showId));
            try {
                if (change.test(showId)) {
                    changed++;
                } else {
                    unchanged++;
                }
            } catch (ErrorResponseException e) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add(LocalizedText.of("tvShow.batch.failure").with("id", showId)
                            .with("message", LocalizedTexts.of(e)));
                }
            }
        }
        context.reportProgress(showIds.size(), showIds.size());
        log.info("TV show batch edit: {} changed, {} unchanged, {} failed", changed, unchanged, failed);
        return new TvShowBatchResult(changed, unchanged, failed, failures);
    }

    /**
     * @param failures the first errors with the TV show ID, at most {@link #MAX_LISTED_FAILURES}
     */
    public record TvShowBatchResult(int changed, int unchanged, int failed, List<LocalizedText> failures) {

        public TvShowBatchResult {
            failures = List.copyOf(failures);
        }
    }
}
