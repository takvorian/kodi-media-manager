package de.tiktak.kmm.kodi;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieBatchService;
import de.tiktak.kmm.tvshow.TvShowBatchService;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import lombok.RequiredArgsConstructor;

/**
 * Updates a selection of movies or TV shows in Kodi as a batch editing job, or a single TV show or season with its
 * episodes. TV shows always come with their episodes. In a batch, a device that does not answer is skipped for the
 * rest of the run: every further attempt would only fail after the timeout, and with a large selection the job would
 * otherwise run for a very long time.
 */
@Service
@RequiredArgsConstructor
public class KodiBatchRefreshService {

    /** Prefix per TV show; at most one refresh of the show or one of its seasons runs at a time. */
    static final String TV_SHOW_JOB_KEY_PREFIX = "kodi-tvshow:";

    private final KodiHostService hostService;
    private final KodiSyncService syncService;
    private final MovieBatchService movieBatch;
    private final TvShowBatchService showBatch;
    private final TvShowMetadataService tvShowService;
    private final JobService jobService;

    /** @throws KodiProblemException if no Kodi device is set up */
    public JobSnapshot startMovies(List<Long> movieIds) {
        Run run = new Run(requireTargets());
        return movieBatch.startFill(LocalizedText.of("kodi.batch.refreshMovies"), movieIds,
                movieId -> run.refresh(targets -> syncService.refreshMovie(movieId, targets)));
    }

    /** @throws KodiProblemException if no Kodi device is set up */
    public JobSnapshot startTvShows(List<Long> showIds) {
        Run run = new Run(requireTargets());
        return showBatch.startFill(LocalizedText.of("kodi.batch.refreshTvShows"), showIds,
                showId -> run.refresh(targets -> syncService.refreshTvShowWithEpisodes(showId, OptionalInt.empty(),
                        targets, KodiProgress.NONE)));
    }

    /**
     * Updates the TV show with all its episodes, or only the episodes of one season, as a job with one step per
     * episode. Its result are the {@link KodiSyncResult}s of all devices.
     *
     * @param season season number, or empty for the whole TV show
     * @throws KodiProblemException if no Kodi device is set up
     */
    public JobSnapshot startTvShow(long showId, OptionalInt season) {
        List<KodiTarget> targets = requireTargets();
        String title = tvShowService.findShow(showId).metadata().title();
        LocalizedText jobTitle = season.isEmpty()
                ? LocalizedText.of("kodi.job.refreshTvShow").with("title", title)
                : LocalizedText.of("kodi.job.refreshSeason").with("title", title).with("season", season.getAsInt());
        return jobService.submit(TV_SHOW_JOB_KEY_PREFIX + showId, jobTitle,
                context -> syncService.refreshTvShowWithEpisodes(showId, season, targets, progressOf(context)));
    }

    private static KodiProgress progressOf(JobContext context) {
        return (done, total) -> {
            context.throwIfCancellationRequested();
            context.reportProgress(done, total);
        };
    }

    private List<KodiTarget> requireTargets() {
        List<KodiTarget> targets = hostService.targets();
        if (targets.isEmpty()) {
            throw KodiProblemException.noHosts();
        }
        return targets;
    }

    /** A run remembers which devices still answer. */
    private static final class Run {

        private final List<KodiTarget> targets;

        private Run(List<KodiTarget> targets) {
            this.targets = new ArrayList<>(targets);
        }

        /**
         * @return whether Kodi did anything
         * @throws KodiProblemException if a device could not update the entry
         */
        boolean refresh(Function<List<KodiTarget>, List<KodiSyncResult>> refresh) {
            if (targets.isEmpty()) {
                throw KodiProblemException.noHostLeft();
            }
            List<KodiSyncResult> results = refresh.apply(List.copyOf(targets));
            results.stream()
                    .filter(result -> result.outcome() == KodiSyncOutcome.FAILED)
                    .forEach(result -> targets.removeIf(target -> target.id() == result.hostId()));
            List<LocalizedText> problems = results.stream()
                    .filter(KodiSyncResult::failed)
                    .map(result -> LocalizedText.of("kodi.hostProblem").with("host", result.host())
                            .with("message", result.message()))
                    .toList();
            if (!problems.isEmpty()) {
                throw KodiProblemException.refreshFailed(problems);
            }
            return results.stream().anyMatch(result -> result.outcome() != KodiSyncOutcome.NOT_IN_KODI);
        }
    }
}
