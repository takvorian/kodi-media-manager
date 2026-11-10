package de.tiktak.kmm.tvshow;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.removal.RemovalJob;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.removal.RemovalRequest;
import de.tiktak.kmm.removal.RemovalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes episodes, seasons and TV shows: from the library only, or with their files. The preview changes nothing;
 * the removal runs as a job: first the files are deleted, then the entries, then Kodi is told on all devices with
 * realtime sync. There is no undo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowRemovalService {

    static final String JOB_KEY_PREFIX = "removal:";

    private final TvShowRemovalPlanner planner;
    private final TvShowRemover remover;
    private final JobService jobService;

    /**
     * @throws TvShowProblemException if the episode does not exist
     */
    public RemovalPlan planEpisode(long episodeId) {
        return planner.episode(episodeId).plan();
    }

    /**
     * @throws TvShowProblemException  if the TV show does not exist
     * @throws RemovalProblemException if the season has no episodes
     */
    public RemovalPlan planSeason(long showId, int season) {
        return planner.season(showId, season).plan();
    }

    /**
     * @throws TvShowProblemException if the TV show does not exist
     */
    public RemovalPlan planShow(long showId) {
        return planner.show(showId).plan();
    }

    /**
     * Removes an episode, and with a multi-episode file all episodes of the file, as a job.
     *
     * @throws RemovalProblemException if files are to be deleted but must not be
     */
    public JobSnapshot removeEpisode(long episodeId, RemovalRequest request) {
        TvShowRemoval removal = planner.episode(episodeId);
        return start(JOB_KEY_PREFIX + "episode:" + episodeId,
                LocalizedText.of("removal.job.entry").with("title", removal.subject()), removal, request);
    }

    /**
     * Removes all episodes of a season as a job.
     *
     * @throws RemovalProblemException if the season has no episodes or files are to be deleted but must not be
     */
    public JobSnapshot removeSeason(long showId, int season, RemovalRequest request) {
        TvShowRemoval removal = planner.season(showId, season);
        return start(JOB_KEY_PREFIX + "season:" + showId + ":" + season, LocalizedText.of("removal.job.season")
                .with("title", removal.showTitle()).with("season", season), removal, request);
    }

    /**
     * Removes a TV show with all episodes as a job.
     *
     * @throws RemovalProblemException if files are to be deleted but must not be
     */
    public JobSnapshot removeShow(long showId, RemovalRequest request) {
        TvShowRemoval removal = planner.show(showId);
        return start(JOB_KEY_PREFIX + "tvshow:" + showId,
                LocalizedText.of("removal.job.entry").with("title", removal.subject()), removal, request);
    }

    private JobSnapshot start(String key, LocalizedText title, TvShowRemoval removal, RemovalRequest request) {
        boolean deleteFiles = request.deleteFiles();
        RemovalJob.requireAllowed(removal.files(), deleteFiles);
        return jobService.submit(key, RemovalJob.title(title, deleteFiles),
                context -> remove(removal, deleteFiles, context));
    }

    RemovalResult remove(TvShowRemoval removal, boolean deleteFiles, JobContext context) {
        RemovalResult result = RemovalJob.run(removal.files(), deleteFiles, removal.entries().size(),
                () -> remover.remove(removal), context);
        log.info("Removed {} entr(ies) of TV show {}, {} file(s) deleted, {} failure(s)", result.removedEntries(),
                removal.showId(), result.deletedFiles(), result.failures().size());
        return result;
    }
}
