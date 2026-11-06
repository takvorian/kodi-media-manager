package de.tiktak.kmm.tvshow;

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
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;
import de.tiktak.kmm.web.LocalizedTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Renames TV shows, season folders and episode files after patterns, for a whole source as a background job or per
 * TV show. The preview changes nothing; renaming computes the plan again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowRenameService {

    static final String JOB_KEY_PREFIX = "tv-rename:";
    static final int MAX_LISTED_PROBLEMS = 20;
    private static final LocalizedText PLANNING_MESSAGE = LocalizedText.of("tvShow.rename.planning");
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final TvShowRenamePlanning planning;
    private final TvShowRenamer renamer;
    private final JobService jobService;
    private final SettingsService settingsService;

    /**
     * @return per TV show of the source, what renaming would change, sorted by folder
     * @throws TvRenameProblemException if the source contains no TV shows or a pattern is invalid
     */
    public List<TvShowRenamePlan> preview(long sourceId, TvRenamePatterns patterns) {
        return planning.planSource(sourceId, patterns.parse()).plans();
    }

    /**
     * @throws TvRenameProblemException if a pattern is invalid
     */
    public TvShowRenamePlan previewShow(long showId, TvRenamePatterns patterns) {
        return planning.planShow(showId, patterns.parse()).plans().getFirst();
    }

    /**
     * Renames a TV show.
     *
     * @return the executed plan; with {@link Status#FAILED} with the reason, with {@link Status#CONFLICT} nothing has
     *         changed
     */
    public TvShowRenamePlan renameShow(long showId, TvRenamePatterns patterns) {
        TvShowRenamePlanning.Planned planned = planning.planShow(showId, patterns.parse());
        TvShowRenamePlan plan = planned.plans().getFirst();
        return plan.status() == Status.RENAME ? apply(plan, planned) : plan;
    }

    /**
     * Renames a TV show with the patterns from the settings if the trigger is switched on there.
     *
     * @return the executed plan, or empty if the trigger is switched off
     */
    public Optional<TvShowRenamePlan> renameIfEnabled(long showId, TvRenameTrigger trigger) {
        RenameSettings settings = settingsService.current().rename();
        if (!trigger.isEnabled(settings)) {
            return Optional.empty();
        }
        TvShowRenamePlan plan = renameShow(showId, new TvRenamePatterns(settings.tvShowFolder(),
                settings.seasonFolder(), settings.episodeFile()));
        if (plan.status() == Status.CONFLICT || plan.status() == Status.FAILED) {
            log.warn("TV show {} not renamed automatically: {}", showId, english(plan.message()));
        }
        return Optional.of(plan);
    }

    /**
     * Renames the season folders and episode files of a season with the patterns from the settings if the trigger is
     * switched on there. The show folder stays as it is.
     *
     * @return the executed plan, or empty if the trigger is switched off
     */
    public Optional<TvShowRenamePlan> renameSeasonIfEnabled(long showId, int season, TvRenameTrigger trigger) {
        RenameSettings settings = settingsService.current().rename();
        if (!trigger.isEnabled(settings)) {
            return Optional.empty();
        }
        TvRenamePatterns patterns = new TvRenamePatterns(settings.tvShowFolder(), settings.seasonFolder(),
                settings.episodeFile());
        TvShowRenamePlanning.Planned planned = planning.planSeason(showId, season, patterns.parse());
        TvShowRenamePlan plan = planned.plans().getFirst();
        TvShowRenamePlan result = plan.status() == Status.RENAME ? apply(plan, planned) : plan;
        if (result.status() == Status.CONFLICT || result.status() == Status.FAILED) {
            log.warn("Season {} of TV show {} not renamed automatically: {}", season, showId,
                    english(result.message()));
        }
        return Optional.of(result);
    }

    /**
     * Renames the TV shows of a source in the background.
     *
     * @throws TvRenameProblemException if a pattern is invalid
     */
    public JobSnapshot startSourceRename(long sourceId, TvRenameRequest request) {
        TvShowRenamePlanner.Patterns patterns = request.patterns().parse();
        LocalizedText title = LocalizedText.of("tvShow.rename.jobTitle")
                .with("source", sourceService.require(sourceId).getName());
        Set<Long> selected = request.showIds() == null ? null : new HashSet<>(request.showIds());
        return jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                context -> renameSource(sourceId, patterns, selected, context));
    }

    TvRenameResult renameSource(long sourceId, TvShowRenamePlanner.Patterns patterns, Set<Long> selected,
                                JobContext context) {
        context.reportProgress(0, UNKNOWN_TOTAL, PLANNING_MESSAGE);
        TvShowRenamePlanning.Planned planned = planning.planSource(sourceId, patterns);
        List<TvShowRenamePlan> plans = planned.plans().stream()
                .filter(plan -> selected == null || selected.contains(plan.showId()))
                .toList();
        Tally tally = new Tally();
        for (TvShowRenamePlan plan : plans) {
            context.throwIfCancellationRequested();
            context.reportProgress(tally.count(), plans.size(), plan.title());
            if (plan.status() == Status.RENAME) {
                tally.addExecuted(apply(plan, planned));
            } else {
                tally.addPlanned(plan);
            }
        }
        TvRenameResult result = tally.result(sourceId);
        log.info("Renamed TV shows of source {}: {} renamed, {} unchanged, {} skipped, {} failed", sourceId,
                result.renamed(), result.unchanged(), result.skipped(), result.failed());
        return result;
    }

    private TvShowRenamePlan apply(TvShowRenamePlan plan, TvShowRenamePlanning.Planned planned) {
        try {
            renamer.rename(plan, planned.sourceRoot());
            return plan;
        } catch (IOException e) {
            log.warn("TV show {} not renamed: {}", plan.showId(), e.getMessage());
            return new TvShowRenamePlan(plan.showId(), plan.title(), plan.currentDirectory(), plan.targetDirectory(),
                    plan.files(), Status.FAILED,
                    LocalizedText.of("tvShow.rename.failed").with("reason", LocalizedTexts.of(e)));
        }
    }

    private static String english(LocalizedText message) {
        return message == null ? null : Messages.english(message);
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

        /** A TV show the file system renamed or refused. */
        void addExecuted(TvShowRenamePlan plan) {
            if (plan.status() == Status.FAILED) {
                failed++;
                problem(showProblem(plan));
            } else {
                renamed++;
                episodeProblems(plan);
            }
        }

        /** A TV show that did not have to or could not be renamed. */
        void addPlanned(TvShowRenamePlan plan) {
            if (plan.status() == Status.UNCHANGED) {
                unchanged++;
            } else {
                skipped++;
                problem(showProblem(plan));
                episodeProblems(plan);
            }
        }

        private void episodeProblems(TvShowRenamePlan plan) {
            plan.files().stream()
                    .filter(file -> file.status() != Status.RENAME)
                    .forEach(file -> problem(LocalizedText.of("tvShow.rename.episodeProblem")
                            .with("title", plan.title())
                            .with("episodes", file.episodes())
                            .with("message", file.message())));
        }

        private static LocalizedText showProblem(TvShowRenamePlan plan) {
            return LocalizedText.of("tvShow.rename.showProblem").with("title", plan.title())
                    .with("message", plan.message());
        }

        private void problem(LocalizedText message) {
            if (problems.size() < MAX_LISTED_PROBLEMS) {
                problems.add(message);
            }
        }

        TvRenameResult result(long sourceId) {
            return new TvRenameResult(sourceId, renamed, unchanged, skipped, failed, problems);
        }
    }
}
