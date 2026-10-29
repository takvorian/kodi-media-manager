package de.tiktak.kmm.movie;

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
    private final MovieTrailerService trailerService;
    private final JobService jobService;

    public JobSnapshot start(MovieBatchEdit edit) {
        return jobService.submit(JOB_KEY, "%d Filme bearbeiten".formatted(edit.movieIds().size()),
                context -> apply(edit, context));
    }

    /** Trägt für Filme ohne Trailer den besten Vorschlag ein, als Job wie die Stapel-Bearbeitung. */
    public JobSnapshot startTrailerFill(List<Long> movieIds) {
        return startFill("Fehlende Trailer für %d Filme holen", movieIds, trailerService::fillMissing);
    }

    /**
     * Ändert jeden Film der Auswahl als Job wie die Stapel-Bearbeitung.
     *
     * @param title  Titel des Jobs mit {@code %d} für die Anzahl der Filme
     * @param change Änderung je Film; liefert, ob sich etwas geändert hat
     */
    public JobSnapshot startFill(String title, List<Long> movieIds, LongPredicate change) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(movieIds));
        return jobService.submit(JOB_KEY, title.formatted(distinct.size()), context -> apply(distinct, change,
                context));
    }

    MovieBatchResult apply(MovieBatchEdit edit, JobContext context) {
        return apply(edit.movieIds(), movieId -> change(movieId, edit), context);
    }

    MovieBatchResult apply(List<Long> movieIds, LongPredicate change, JobContext context) {
        List<String> failures = new ArrayList<>();
        int changed = 0;
        int unchanged = 0;
        int failed = 0;
        for (long movieId : movieIds) {
            context.throwIfCancellationRequested();
            context.reportProgress(changed + unchanged + failed, movieIds.size(), "Film " + movieId);
            try {
                if (change.test(movieId)) {
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
