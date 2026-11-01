package de.tiktak.kmm.kodi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieBatchService;
import de.tiktak.kmm.tvshow.TvShowBatchService;
import lombok.RequiredArgsConstructor;

/**
 * Aktualisiert eine Auswahl von Filmen oder Serien in Kodi als Job der Stapel-Bearbeitung. Ein Gerät, das nicht
 * antwortet, wird für den Rest des Laufs übersprungen: Jeder weitere Versuch würde erst nach dem Timeout scheitern,
 * und bei einer großen Auswahl läuft der Job sonst sehr lange.
 */
@Service
@RequiredArgsConstructor
public class KodiBatchRefreshService {

    private final KodiHostService hostService;
    private final KodiSyncService syncService;
    private final MovieBatchService movieBatch;
    private final TvShowBatchService showBatch;

    /** @throws KodiProblemException wenn kein Kodi-Gerät eingerichtet ist */
    public JobSnapshot startMovies(List<Long> movieIds) {
        Run run = new Run(requireTargets());
        String title = movieIds.size() == 1 ? "1 Film in Kodi aktualisieren" : "%d Filme in Kodi aktualisieren";
        return movieBatch.startFill(title, movieIds,
                movieId -> run.refresh(targets -> syncService.refreshMovie(movieId, targets)));
    }

    /** @throws KodiProblemException wenn kein Kodi-Gerät eingerichtet ist */
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

    /** Ein Lauf merkt sich, welche Geräte noch antworten. */
    private static final class Run {

        private final List<KodiTarget> targets;

        private Run(List<KodiTarget> targets) {
            this.targets = new ArrayList<>(targets);
        }

        /**
         * @return ob Kodi etwas getan hat
         * @throws KodiProblemException wenn ein Gerät den Eintrag nicht aktualisieren konnte
         */
        boolean refresh(Function<List<KodiTarget>, List<KodiSyncResult>> refresh) {
            if (targets.isEmpty()) {
                throw KodiProblemException.noHostLeft();
            }
            List<KodiSyncResult> results = refresh.apply(List.copyOf(targets));
            results.stream()
                    .filter(result -> result.outcome() == KodiSyncOutcome.FAILED)
                    .forEach(result -> targets.removeIf(target -> target.id() == result.hostId()));
            String problems = results.stream()
                    .filter(KodiSyncResult::failed)
                    .map(result -> "%s: %s".formatted(result.host(), result.message()))
                    .collect(Collectors.joining("; "));
            if (!problems.isEmpty()) {
                throw KodiProblemException.refreshFailed(problems);
            }
            return results.stream().anyMatch(result -> result.outcome() != KodiSyncOutcome.NOT_IN_KODI);
        }
    }
}
