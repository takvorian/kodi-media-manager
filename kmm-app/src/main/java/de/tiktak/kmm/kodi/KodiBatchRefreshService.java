package de.tiktak.kmm.kodi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieBatchService;
import de.tiktak.kmm.tvshow.TvShowBatchService;
import lombok.RequiredArgsConstructor;

/**
 * Updates a selection of movies or TV shows in Kodi as a batch editing job. A device that does not answer is skipped
 * for the rest of the run: every further attempt would only fail after the timeout, and with a large selection the
 * job would otherwise run for a very long time.
 */
@Service
@RequiredArgsConstructor
public class KodiBatchRefreshService {

    private final KodiHostService hostService;
    private final KodiSyncService syncService;
    private final MovieBatchService movieBatch;
    private final TvShowBatchService showBatch;

    /** @throws KodiProblemException if no Kodi device is set up */
    public JobSnapshot startMovies(List<Long> movieIds) {
        Run run = new Run(requireTargets());
        String title = movieIds.size() == 1 ? "1 Film in Kodi aktualisieren" : "%d Filme in Kodi aktualisieren";
        return movieBatch.startFill(title, movieIds,
                movieId -> run.refresh(targets -> syncService.refreshMovie(movieId, targets)));
    }

    /** @throws KodiProblemException if no Kodi device is set up */
    public JobSnapshot startTvShows(List<Long> showIds) {
        Run run = new Run(requireTargets());
        String title = showIds.size() == 1 ? "1 Serie in Kodi aktualisieren" : "%d Serien in Kodi aktualisieren";
        return showBatch.startFill(title, showIds,
                showId -> run.refresh(targets -> syncService.refreshTvShow(showId, targets)));
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
