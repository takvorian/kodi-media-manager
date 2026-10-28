package de.tiktak.kmm.movie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Benennt Filme nach Mustern um, für eine ganze Quelle als Hintergrund-Job oder einzeln. Die Vorschau ändert
 * nichts; beim Umbenennen wird der Plan neu berechnet, damit zwischenzeitliche Änderungen auffallen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRenameService {

    static final String JOB_KEY_PREFIX = "movie-rename:";
    static final int MAX_LISTED_PROBLEMS = 20;
    private static final String PLANNING_MESSAGE = "Neue Namen werden berechnet";
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final MovieRenamePlanning planning;
    private final MovieRenamer renamer;
    private final JobService jobService;

    /**
     * @return je Film der Quelle, was das Umbenennen ändern würde, sortiert nach Ordner
     * @throws MovieRenameProblemException wenn die Quelle keine Filme enthält oder ein Muster ungültig ist
     */
    public List<MovieRenamePlan> preview(long sourceId, MovieRenamePatterns patterns) {
        return planning.planSource(sourceId, patterns.parse()).plans();
    }

    /**
     * @throws MovieRenameProblemException wenn ein Muster ungültig ist
     */
    public MovieRenamePlan previewMovie(long movieId, MovieRenamePatterns patterns) {
        return planning.planMovie(movieId, patterns.parse()).plans().getFirst();
    }

    /**
     * Benennt einen Film um.
     *
     * @return der ausgeführte Plan; bei {@link Status#FAILED} mit dem Grund, bei {@link Status#CONFLICT} ist nichts
     *         geändert
     */
    public MovieRenamePlan renameMovie(long movieId, MovieRenamePatterns patterns) {
        MovieRenamePlanning.Planned planned = planning.planMovie(movieId, patterns.parse());
        MovieRenamePlan plan = planned.plans().getFirst();
        return plan.status() == Status.RENAME ? apply(plan, planned) : plan;
    }

    /**
     * Benennt die Filme einer Quelle im Hintergrund um.
     *
     * @throws MovieRenameProblemException wenn ein Muster ungültig ist
     */
    public JobSnapshot startSourceRename(long sourceId, MovieRenameRequest request) {
        MovieRenamePatterns.Parsed patterns = request.patterns().parse();
        String title = "Filme umbenennen „%s“".formatted(sourceService.require(sourceId).getName());
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
                    plan.renames(), Status.FAILED, "Umbenennen fehlgeschlagen: " + e.getMessage());
        }
    }

    private static final class Tally {

        private final List<String> problems = new ArrayList<>();
        private int renamed;
        private int unchanged;
        private int skipped;
        private int failed;

        int count() {
            return renamed + unchanged + skipped + failed;
        }

        /** Ein Film, den das Dateisystem umbenannt oder abgelehnt hat. */
        void addExecuted(MovieRenamePlan plan) {
            if (plan.status() == Status.FAILED) {
                failed++;
                problem(plan);
            } else {
                renamed++;
            }
        }

        /** Ein Film, der nicht umbenannt werden musste oder konnte. */
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
                problems.add("„%s“: %s".formatted(plan.title(), plan.message()));
            }
        }

        MovieRenameResult result(long sourceId) {
            return new MovieRenameResult(sourceId, renamed, unchanged, skipped, failed, problems);
        }
    }
}
