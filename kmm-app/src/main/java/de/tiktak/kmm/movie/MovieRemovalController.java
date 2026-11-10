package de.tiktak.kmm.movie;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Removing movies, one or a selection: the preview changes nothing, the removal runs as a job.
 */
@RestController
@RequiredArgsConstructor
public class MovieRemovalController {

    public static final String MOVIE_REMOVAL_PATH = MovieController.MOVIE_PATH + "/removal";
    public static final String SELECTION_REMOVAL_PATH = MovieController.MOVIES_PATH + "/removal";
    public static final String SELECTION_PLAN_PATH = SELECTION_REMOVAL_PATH + "/plan";
    static final int MAX_MOVIES = 20_000;

    private final MovieRemovalService removalService;

    @GetMapping(MOVIE_REMOVAL_PATH)
    public RemovalPlan planMovie(@PathVariable long movieId) {
        return removalService.plan(List.of(movieId));
    }

    @PostMapping(MOVIE_REMOVAL_PATH)
    public ResponseEntity<JobSnapshot> removeMovie(@PathVariable long movieId, @RequestBody RemovalRequest request) {
        return accepted(removalService.remove(List.of(movieId), request.deleteFiles()));
    }

    @PostMapping(SELECTION_PLAN_PATH)
    public RemovalPlan planSelection(@Valid @RequestBody Movies selection) {
        return removalService.plan(selection.movieIds());
    }

    @PostMapping(SELECTION_REMOVAL_PATH)
    public ResponseEntity<JobSnapshot> removeSelection(@Valid @RequestBody Selection selection) {
        return accepted(removalService.remove(selection.movieIds(), selection.deleteFiles()));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Movies(@NotNull @NotEmpty @Size(max = MAX_MOVIES) List<@NotNull Long> movieIds) {
    }

    /**
     * @param deleteFiles whether the files are deleted for good as well
     */
    public record Selection(@NotNull @NotEmpty @Size(max = MAX_MOVIES) List<@NotNull Long> movieIds,
                            boolean deleteFiles) {
    }
}
