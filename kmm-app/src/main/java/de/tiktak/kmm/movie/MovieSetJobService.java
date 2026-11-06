package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongPredicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Changes to movie sets that affect the NFOs of their movies, as a background job: change or delete a set, add and
 * remove movies, complete all sets.
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
     * Takes the data over right away and then rewrites the NFOs of the movies if name or overview changed.
     *
     * @throws MovieSetProblemException if the set does not exist or the name is taken
     */
    public JobSnapshot update(long setId, MovieSetData data) {
        List<Long> movies = setService.update(setId, data);
        return submit(LocalizedText.of("movieSet.save.jobTitle").with("name", data.name()), context ->
                apply(movies, membership::rewrite, context));
    }

    /** Takes all movies out of the set and deletes it. */
    public JobSnapshot delete(long setId) {
        MovieSetDetails set = setService.find(setId);
        List<Long> movies = setService.findMovieIds(setId);
        return submit(LocalizedText.of("movieSet.delete.jobTitle").with("name", set.data().name()), context -> {
            MovieSetChangeResult result = apply(movies, movieId -> membership.assign(movieId, null), context);
            if (result.failed() == 0) {
                setService.deleteEmpty(setId);
            }
            return result;
        });
    }

    /** Adds movies to the set; they leave their former set. */
    public JobSnapshot addMovies(long setId, List<Long> movieIds) {
        MovieSetDetails set = setService.find(setId);
        return submit(LocalizedText.of("movieSet.addMovies.jobTitle").with("name", set.data().name()), context ->
                apply(distinct(movieIds), movieId -> membership.assign(movieId, setId), context));
    }

    /** Takes movies out of the set. */
    public JobSnapshot removeMovies(long setId, List<Long> movieIds) {
        MovieSetDetails set = setService.find(setId);
        List<Long> members = setService.findMovieIds(setId);
        List<Long> removed = distinct(movieIds).stream().filter(members::contains).toList();
        return submit(LocalizedText.of("movieSet.removeMovies.jobTitle").with("name", set.data().name()), context ->
                apply(removed, movieId -> membership.assign(movieId, null), context));
    }

    /** Completes missing overviews and images of all sets with a TMDB ID; existing ones stay. */
    public JobSnapshot completeAll() {
        return jobService.submit(JOB_KEY, LocalizedText.of("movieSet.completeAll.jobTitle"), this::complete);
    }

    MovieSetCompletionResult complete(JobContext context) {
        List<MovieSetSummary> sets = setService.findAll();
        Set<LocalizedText> problems = new LinkedHashSet<>();
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
                problems.add(LocalizedText.of("movieSet.failure")
                        .with("set", set.name())
                        .with("reason", LocalizedReasons.of(e)));
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

    private JobSnapshot submit(LocalizedText title, JobTask<MovieSetChangeResult> task) {
        return jobService.submit(JOB_KEY, title, task);
    }

    MovieSetChangeResult apply(List<Long> movieIds, LongPredicate change, JobContext context) {
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
        log.info("Movie set change: {} changed, {} unchanged, {} failed", changed, unchanged, failed);
        return new MovieSetChangeResult(changed, unchanged, failed, failures);
    }

    private static List<Long> distinct(List<Long> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
