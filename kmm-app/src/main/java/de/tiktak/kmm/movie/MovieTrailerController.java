package de.tiktak.kmm.movie;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.trailer.TrailerOptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Trailer eines Films als Link in der NFO.
 */
@RestController
@RequiredArgsConstructor
public class MovieTrailerController {

    public static final String TRAILER_PATH = MovieController.MOVIE_PATH + "/trailer";
    public static final String CANDIDATES_PATH = TRAILER_PATH + "/candidates";
    public static final String FILL_PATH = MovieController.MOVIES_PATH + "/trailers/fill";
    static final int MAX_MOVIES = 20_000;

    private final MovieTrailerService trailerService;
    private final MovieBatchService batchService;

    @GetMapping(CANDIDATES_PATH)
    public TrailerOptions candidates(@PathVariable long movieId) {
        return trailerService.candidates(movieId);
    }

    @PutMapping(TRAILER_PATH)
    public MovieDetails apply(@PathVariable long movieId, @Valid @RequestBody TrailerRequest request) {
        return trailerService.apply(movieId, request.url());
    }

    @DeleteMapping(TRAILER_PATH)
    public MovieDetails remove(@PathVariable long movieId) {
        return trailerService.remove(movieId);
    }

    /** Holt für die Filme ohne Trailer den besten Vorschlag, als Job. */
    @PostMapping(FILL_PATH)
    public ResponseEntity<JobSnapshot> fillMissing(@Valid @RequestBody Movies movies) {
        JobSnapshot job = batchService.startTrailerFill(movies.movieIds());
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record TrailerRequest(@NotBlank @Size(max = Movie.MAX_TRAILER_LENGTH) String url) {
    }

    public record Movies(@NotNull @NotEmpty @Size(max = MAX_MOVIES) List<@NotNull Long> movieIds) {
    }
}
