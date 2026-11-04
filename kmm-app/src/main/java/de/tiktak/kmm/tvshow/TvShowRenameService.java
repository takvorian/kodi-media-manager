package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Benennt Serien, Staffelordner und Episodendateien nach Mustern um, für eine ganze Quelle als Hintergrund-Job
 * oder je Serie. Die Vorschau ändert nichts; beim Umbenennen wird der Plan neu berechnet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowRenameService {

    static final String JOB_KEY_PREFIX = "tv-rename:";
    static final int MAX_LISTED_PROBLEMS = 20;
    private static final String PLANNING_MESSAGE = "Neue Namen werden berechnet";
    private static final int UNKNOWN_TOTAL = 0;

    private final MediaSourceService sourceService;
    private final TvShowRenamePlanning planning;
    private final TvShowRenamer renamer;
    private final JobService jobService;
    private final SettingsService settingsService;

    /**
     * @return je Serie der Quelle, was das Umbenennen ändern würde, sortiert nach Ordner
     * @throws TvRenameProblemException wenn die Quelle keine Serien enthält oder ein Muster ungültig ist
     */
    public List<TvShowRenamePlan> preview(long sourceId, TvRenamePatterns patterns) {
        return planning.planSource(sourceId, patterns.parse()).plans();
    }

    /**
     * @throws TvRenameProblemException wenn ein Muster ungültig ist
     */
    public TvShowRenamePlan previewShow(long showId, TvRenamePatterns patterns) {
        return planning.planShow(showId, patterns.parse()).plans().getFirst();
    }

    /**
     * Benennt eine Serie um.
     *
     * @return der ausgeführte Plan; bei {@link Status#FAILED} mit dem Grund, bei {@link Status#CONFLICT} ist nichts
     *         geändert
     */
    public TvShowRenamePlan renameShow(long showId, TvRenamePatterns patterns) {
        TvShowRenamePlanning.Planned planned = planning.planShow(showId, patterns.parse());
        TvShowRenamePlan plan = planned.plans().getFirst();
        return plan.status() == Status.RENAME ? apply(plan, planned) : plan;
    }

    /**
     * Benennt eine Serie mit den Mustern aus den Einstellungen um, wenn der Auslöser dort eingeschaltet ist.
     *
     * @return der ausgeführte Plan oder leer, wenn der Auslöser ausgeschaltet ist
     */
    public Optional<TvShowRenamePlan> renameIfEnabled(long showId, TvRenameTrigger trigger) {
        RenameSettings settings = settingsService.current().rename();
        if (!trigger.isEnabled(settings)) {
            return Optional.empty();
        }
        TvShowRenamePlan plan = renameShow(showId, new TvRenamePatterns(settings.tvShowFolder(),
                settings.seasonFolder(), settings.episodeFile()));
        if (plan.status() == Status.CONFLICT || plan.status() == Status.FAILED) {
            log.warn("TV show {} not renamed automatically: {}", showId, plan.message());
        }
        return Optional.of(plan);
    }

    /**
     * Benennt die Staffel-Ordner und Episodendateien einer Staffel mit den Mustern aus den Einstellungen um, wenn der
     * Auslöser dort eingeschaltet ist. Der Serienordner bleibt, wie er ist.
     *
     * @return der ausgeführte Plan oder leer, wenn der Auslöser ausgeschaltet ist
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
            log.warn("Season {} of TV show {} not renamed automatically: {}", season, showId, result.message());
        }
        return Optional.of(result);
    }

    /**
     * Benennt die Serien einer Quelle im Hintergrund um.
     *
     * @throws TvRenameProblemException wenn ein Muster ungültig ist
     */
    public JobSnapshot startSourceRename(long sourceId, TvRenameRequest request) {
        TvShowRenamePlanner.Patterns patterns = request.patterns().parse();
        String title = "Serien umbenennen „%s“".formatted(sourceService.require(sourceId).getName());
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
                    plan.files(), Status.FAILED, "Umbenennen fehlgeschlagen: " + e.getMessage());
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

        /** Eine Serie, die das Dateisystem umbenannt oder abgelehnt hat. */
        void addExecuted(TvShowRenamePlan plan) {
            if (plan.status() == Status.FAILED) {
                failed++;
                problem("„%s“: %s".formatted(plan.title(), plan.message()));
            } else {
                renamed++;
                episodeProblems(plan);
            }
        }

        /** Eine Serie, die nicht umbenannt werden musste oder konnte. */
        void addPlanned(TvShowRenamePlan plan) {
            if (plan.status() == Status.UNCHANGED) {
                unchanged++;
            } else {
                skipped++;
                problem("„%s“: %s".formatted(plan.title(), plan.message()));
                episodeProblems(plan);
            }
        }

        private void episodeProblems(TvShowRenamePlan plan) {
            plan.files().stream()
                    .filter(file -> file.status() != Status.RENAME)
                    .forEach(file -> problem("„%s“ %s: %s".formatted(plan.title(), file.episodes(), file.message())));
        }

        private void problem(String message) {
            if (problems.size() < MAX_LISTED_PROBLEMS) {
                problems.add(message);
            }
        }

        TvRenameResult result(long sourceId) {
            return new TvRenameResult(sourceId, renamed, unchanged, skipped, failed, problems);
        }
    }
}
