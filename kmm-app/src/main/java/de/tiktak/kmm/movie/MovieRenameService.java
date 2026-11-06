package de.tiktak.kmm.movie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Renames movies according to patterns, for a whole source as a background job or one by one. The preview changes
 * nothing; renaming calculates the plan again, so that changes in between are noticed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRenameService {

    static final String JOB_KEY_PREFIX = "movie-rename:";
    static final int MAX_LISTED_PROBLEMS = 20;
    private static final LocalizedText PLANNING_MESSAGE = LocalizedText.of("movie.rename.planning");
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final MovieRenamePlanning planning;
    private final MovieRenamer renamer;
    private final JobService jobService;
    private final SettingsService settingsService;

    /**
     * @return per movie of the source what renaming would change, sorted by folder
     * @throws MovieRenameProblemException if the source does not contain movies or a pattern is invalid
     */
    public List<MovieRenamePlan> preview(long sourceId, MovieRenamePatterns patterns) {
        return planning.planSource(sourceId, patterns.parse()).plans();
    }

    /**
     * @throws MovieRenameProblemException if a pattern is invalid
     */
    public MovieRenamePlan previewMovie(long movieId, MovieRenamePatterns patterns) {
        return planning.planMovie(movieId, patterns.parse()).plans().getFirst();
    }

    /**
     * Renames one movie.
     *
     * @return the executed plan; with the reason for {@link Status#FAILED}, nothing changed for
     *         {@link Status#CONFLICT}
     */
    public MovieRenamePlan renameMovie(long movieId, MovieRenamePatterns patterns) {
        MovieRenamePlanning.Planned planned = planning.planMovie(movieId, patterns.parse());
        MovieRenamePlan plan = planned.plans().getFirst();
        return plan.status() == Status.RENAME ? apply(plan, planned) : plan;
    }

    /**
     * Renames a movie with the patterns from the settings if the trigger is turned on there.
     *
     * @return the executed plan, or empty if the trigger is turned off
     */
    public Optional<MovieRenamePlan> renameIfEnabled(long movieId, RenameTrigger trigger) {
        RenameSettings settings = settingsService.current().rename();
        if (!trigger.isEnabled(settings)) {
            return Optional.empty();
        }
        MovieRenamePlan plan = renameMovie(movieId, new MovieRenamePatterns(settings.movieFolder(), settings.movieFile()));
        if (plan.status() == Status.CONFLICT || plan.status() == Status.FAILED) {
            log.warn("Movie {} not renamed automatically: {}", movieId, Messages.english(plan.message()));
        }
        return Optional.of(plan);
    }

    /**
     * Renames the movies of a source in the background.
     *
     * @throws MovieRenameProblemException if a pattern is invalid
     */
    public JobSnapshot startSourceRename(long sourceId, MovieRenameRequest request) {
        MovieRenamePatterns.Parsed patterns = request.patterns().parse();
        LocalizedText title = LocalizedText.of("movie.rename.jobTitle")
                .with("source", sourceService.require(sourceId).getName());
        Set<Long> selected = request.movieIds() == null ? null : new HashSet<>(request.movieIds());
        return jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                context -> renameSource(sourceId, patterns, selected, context));
    }

    MovieRenameResult renameSource(long sourceId, MovieRenamePatterns.Parsed patterns, Set<Long> selected,
                                   JobContext context) {
        context.reportProgress(0, UNKNOWN_TOTAL, PLANNING_MESSAGE);
        MovieRenamePlanning.Planned planned = planning.planSource(sourceId, patterns);
        List<MovieRenamePlan> plans = planned.plans().stream()
                .filter(plan -> selected == null || selected.contains(plan.movieId()))
                .toList();
        Tally tally = new Tally();
        for (MovieRenamePlan plan : plans) {
            context.throwIfCancellationRequested();
            context.reportProgress(tally.count(), plans.size(), plan.title());
            if (plan.status() == Status.RENAME) {
                tally.addExecuted(apply(plan, planned));
            } else {
                tally.addPlanned(plan);
            }
        }
        MovieRenameResult result = tally.result(sourceId);
        log.info("Renamed movies of source {}: {} renamed, {} unchanged, {} skipped, {} failed", sourceId,
                result.renamed(), result.unchanged(), result.skipped(), result.failed());
        return result;
    }

    private MovieRenamePlan apply(MovieRenamePlan plan, MovieRenamePlanning.Planned planned) {
        try {
            renamer.rename(plan, planned.sourceRoot());
            return plan;
        } catch (IOException e) {
            log.warn("Movie {} not renamed: {}", plan.movieId(), e.getMessage());
            return new MovieRenamePlan(plan.movieId(), plan.title(), plan.currentDirectory(), plan.targetDirectory(),
                    plan.renames(), Status.FAILED,
                    LocalizedText.of("movie.rename.failed").with("reason", LocalizedReasons.of(e)));
        }
    }

    private static final class Tally {

        private final List<LocalizedText> problems = new ArrayList<>();
        private int renamed;
        private int unchanged;
        private int skipped;
        private int failed;

        int count() {
            return renamed + unchanged + skipped + failed;
        }

        /** A movie the file system renamed or refused. */
        void addExecuted(MovieRenamePlan plan) {
            if (plan.status() == Status.FAILED) {
                failed++;
                problem(plan);
            } else {
                renamed++;
            }
        }

        /** A movie that did not need to be or could not be renamed. */
        void addPlanned(MovieRenamePlan plan) {
            if (plan.status() == Status.UNCHANGED) {
                unchanged++;
            } else {
                skipped++;
                problem(plan);
            }
        }

        private void problem(MovieRenamePlan plan) {
            if (problems.size() < MAX_LISTED_PROBLEMS) {
                problems.add(LocalizedText.of("movie.rename.problem")
                        .with("title", plan.title())
                        .with("reason", plan.message()));
            }
        }

        MovieRenameResult result(long sourceId) {
            return new MovieRenameResult(sourceId, renamed, unchanged, skipped, failed, problems);
        }
    }
}
