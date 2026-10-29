package de.tiktak.kmm.tvshow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.LongPredicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stapel-Bearbeitung von Serien als Hintergrund-Job, je Serie in einer eigenen Transaktion. Ein Fehler bei einer
 * Serie hält die übrigen nicht auf. Umbenannt wird dabei nicht.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowBatchService {

    static final String JOB_KEY = "tvshows-batch";
    static final int MAX_LISTED_FAILURES = 20;

    private final TvShowMetadataService metadataService;
    private final TvShowTrailerService trailerService;
    private final JobService jobService;

    public JobSnapshot start(TvShowBatchEdit edit) {
        return jobService.submit(JOB_KEY, "%d Serien bearbeiten".formatted(edit.showIds().size()),
                context -> apply(edit, context));
    }

    /** Trägt für Serien ohne Trailer den besten Vorschlag ein, als Job wie die Stapel-Bearbeitung. */
    public JobSnapshot startTrailerFill(List<Long> showIds) {
        return startFill("Fehlende Trailer für %d Serien holen", showIds, trailerService::fillMissing);
    }

    /**
     * Ändert jede Serie der Auswahl als Job wie die Stapel-Bearbeitung.
     *
     * @param title  Titel des Jobs mit {@code %d} für die Anzahl der Serien
     * @param change Änderung je Serie; liefert, ob sich etwas geändert hat
     */
    public JobSnapshot startFill(String title, List<Long> showIds, LongPredicate change) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(showIds));
        return jobService.submit(JOB_KEY, title.formatted(distinct.size()), context -> apply(distinct, change,
                context));
    }

    TvShowBatchResult apply(TvShowBatchEdit edit, JobContext context) {
        return apply(edit.showIds(), showId -> metadataService.changeShow(showId, edit::apply), context);
    }

    TvShowBatchResult apply(List<Long> showIds, LongPredicate change, JobContext context) {
        List<String> failures = new ArrayList<>();
        int changed = 0;
        int unchanged = 0;
        int failed = 0;
        for (long showId : showIds) {
            context.throwIfCancellationRequested();
            context.reportProgress(changed + unchanged + failed, showIds.size(), "Serie " + showId);
            try {
                if (change.test(showId)) {
                    changed++;
                } else {
                    unchanged++;
                }
            } catch (ErrorResponseException e) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add("Serie %d: %s".formatted(showId, e.getBody().getDetail()));
                }
            }
        }
        context.reportProgress(showIds.size(), showIds.size(), null);
        log.info("TV show batch edit: {} changed, {} unchanged, {} failed", changed, unchanged, failed);
        return new TvShowBatchResult(changed, unchanged, failed, failures);
    }

    /**
     * @param failures die ersten Fehler mit Serien-ID, höchstens {@link #MAX_LISTED_FAILURES}
     */
    public record TvShowBatchResult(int changed, int unchanged, int failed, List<String> failures) {

        public TvShowBatchResult {
            failures = List.copyOf(failures);
        }
    }
}
