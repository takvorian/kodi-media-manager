package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongPredicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Änderungen an Filmsets, die die NFOs ihrer Filme betreffen, als Hintergrund-Job: Set ändern, löschen, Filme
 * hinzufügen und entfernen, alle Sets ergänzen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieSetJobService {

    static final String JOB_KEY = "moviesets";
    static final int MAX_LISTED_FAILURES = 20;

    private final MovieSetService setService;
    private final MovieSetMembership membership;
    private final JobService jobService;
    private final MovieSetScrapeService scrapeService;
    private final MovieSetArtworkFiller artworkFiller;

    /**
     * Übernimmt die Angaben sofort und schreibt danach die NFOs der Filme neu, wenn sich Name oder Beschreibung
     * geändert haben.
     *
     * @throws MovieSetProblemException wenn es das Set nicht gibt oder der Name vergeben ist
     */
    public JobSnapshot update(long setId, MovieSetData data) {
        List<Long> movies = setService.update(setId, data);
        return submit("Filmset „%s“ speichern".formatted(data.name()), context ->
                apply(movies, membership::rewrite, context));
    }

    /** Nimmt alle Filme aus dem Set und löscht es. */
    public JobSnapshot delete(long setId) {
        MovieSetDetails set = setService.find(setId);
        List<Long> movies = setService.findMovieIds(setId);
        return submit("Filmset „%s“ löschen".formatted(set.data().name()), context -> {
            MovieSetChangeResult result = apply(movies, movieId -> membership.assign(movieId, null), context);
            if (result.failed() == 0) {
                setService.deleteEmpty(setId);
            }
            return result;
        });
    }

    /** Fügt Filme dem Set hinzu; sie verlassen dabei ihr bisheriges Set. */
    public JobSnapshot addMovies(long setId, List<Long> movieIds) {
        MovieSetDetails set = setService.find(setId);
        return submit("Filme zu „%s“ hinzufügen".formatted(set.data().name()), context ->
                apply(distinct(movieIds), movieId -> membership.assign(movieId, setId), context));
    }

    /** Nimmt Filme aus dem Set. */
    public JobSnapshot removeMovies(long setId, List<Long> movieIds) {
        MovieSetDetails set = setService.find(setId);
        List<Long> members = setService.findMovieIds(setId);
        List<Long> removed = distinct(movieIds).stream().filter(members::contains).toList();
        return submit("Filme aus „%s“ entfernen".formatted(set.data().name()), context ->
                apply(removed, movieId -> membership.assign(movieId, null), context));
    }

    /** Ergänzt bei allen Sets mit TMDB-ID fehlende Beschreibungen und Bilder; vorhandene bleiben. */
    public JobSnapshot completeAll() {
        return jobService.submit(JOB_KEY, "Alle Sets ergänzen", this::complete);
    }

    MovieSetCompletionResult complete(JobContext context) {
        List<MovieSetSummary> sets = setService.findAll();
        Set<String> problems = new LinkedHashSet<>();
        int overviews = 0;
        int images = 0;
        int withoutCollection = 0;
        for (int index = 0; index < sets.size(); index++) {
            MovieSetSummary set = sets.get(index);
            context.throwIfCancellationRequested();
            context.reportProgress(index, sets.size(), set.name());
            if (set.tmdbCollectionId() == null) {
                withoutCollection++;
                continue;
            }
            try {
                if (set.overview() == null && completeOverview(set)) {
                    overviews++;
                }
                MovieSetArtworkFiller.Fill fill = artworkFiller.fillMissingArtwork(set.id());
                images += fill.saved();
                problems.addAll(fill.warnings());
            } catch (ErrorResponseException e) {
                problems.add("%s: %s".formatted(set.name(), e.getBody().getDetail()));
            }
        }
        context.reportProgress(sets.size(), sets.size());
        log.info("Movie sets completed: {} overviews, {} images, {} without collection", overviews, images,
                withoutCollection);
        return new MovieSetCompletionResult(overviews, images, withoutCollection,
                problems.stream().limit(MAX_LISTED_FAILURES).toList());
    }

    private boolean completeOverview(MovieSetSummary set) {
        Optional<String> overview = scrapeService.fetch(set.tmdbCollectionId()).map(MovieSetResult::overview);
        if (overview.isEmpty()) {
            return false;
        }
        setService.update(set.id(), new MovieSetData(set.name(), overview.get(), set.tmdbCollectionId(),
                set.sortMethod())).forEach(membership::rewrite);
        return true;
    }

    private JobSnapshot submit(String title, JobTask<MovieSetChangeResult> task) {
        return jobService.submit(JOB_KEY, title, task);
    }

    MovieSetChangeResult apply(List<Long> movieIds, LongPredicate change, JobContext context) {
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
        log.info("Movie set change: {} changed, {} unchanged, {} failed", changed, unchanged, failed);
        return new MovieSetChangeResult(changed, unchanged, failed, failures);
    }

    private static List<Long> distinct(List<Long> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
