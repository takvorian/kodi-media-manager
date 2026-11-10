package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.removal.RemovalJob;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.removal.RemovalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes movies: from the library only, or with their files. The preview changes nothing; the removal runs as a
 * job: first the files are deleted, then the entries, then Kodi is told on all devices with realtime sync. There is no
 * undo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRemovalService {

    static final String JOB_KEY_PREFIX = "removal:";

    private final MovieRemovalPlanner planner;
    private final MovieRemover remover;
    private final JobService jobService;

    /**
     * @throws MovieNotFoundException if a movie does not exist
     */
    public RemovalPlan plan(List<Long> movieIds) {
        return planner.movies(movieIds).plan();
    }

    /**
     * Removes the movies as a job.
     *
     * @throws MovieNotFoundException  if a movie does not exist
     * @throws RemovalProblemException if files are to be deleted but must not be
     */
    public JobSnapshot remove(List<Long> movieIds, boolean deleteFiles) {
        MovieRemoval removal = planner.movies(movieIds);
        RemovalJob.requireAllowed(removal.files(), deleteFiles);
        List<MovieRemoval.RemovedMovie> movies = removal.movies();
        boolean single = movies.size() == 1;
        LocalizedText title = single
                ? LocalizedText.of("removal.job.entry").with("title", movies.getFirst().title())
                : LocalizedText.of("removal.job.movies").with("count", movies.size());
        String key = JOB_KEY_PREFIX + (single ? "movie:" + movies.getFirst().id() : "movies");
        return jobService.submit(key, RemovalJob.title(title, deleteFiles),
                context -> remove(removal, deleteFiles, context));
    }

    RemovalResult remove(MovieRemoval removal, boolean deleteFiles, JobContext context) {
        RemovalResult result = RemovalJob.run(removal.files(), deleteFiles, removal.movies().size(),
                () -> remover.remove(removal), context);
        log.info("Removed {} movie(s), {} file(s) deleted, {} failure(s)", result.removedEntries(),
                result.deletedFiles(), result.failures().size());
        return result;
    }
}
