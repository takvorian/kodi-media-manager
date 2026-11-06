package de.tiktak.kmm.tvshow;

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
 * Trailer of a TV show as a link in the {@code tvshow.nfo}.
 */
@RestController
@RequiredArgsConstructor
public class TvShowTrailerController {

    public static final String TRAILER_PATH = TvShowController.SHOW_PATH + "/trailer";
    public static final String CANDIDATES_PATH = TRAILER_PATH + "/candidates";
    public static final String FILL_PATH = TvShowController.SHOWS_PATH + "/trailers/fill";
    static final int MAX_SHOWS = 20_000;

    private final TvShowTrailerService trailerService;
    private final TvShowBatchService batchService;

    @GetMapping(CANDIDATES_PATH)
    public TrailerOptions candidates(@PathVariable long showId) {
        return trailerService.candidates(showId);
    }

    @PutMapping(TRAILER_PATH)
    public TvShowDetails apply(@PathVariable long showId, @Valid @RequestBody TrailerRequest request) {
        return trailerService.apply(showId, request.url());
    }

    @DeleteMapping(TRAILER_PATH)
    public TvShowDetails remove(@PathVariable long showId) {
        return trailerService.remove(showId);
    }

    /** Fetches the best proposal for the TV shows without a trailer, as a job. */
    @PostMapping(FILL_PATH)
    public ResponseEntity<JobSnapshot> fillMissing(@Valid @RequestBody Shows shows) {
        JobSnapshot job = batchService.startTrailerFill(shows.showIds());
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record TrailerRequest(@NotBlank @Size(max = TvShow.MAX_TRAILER_LENGTH) String url) {
    }

    public record Shows(@NotNull @NotEmpty @Size(max = MAX_SHOWS) List<@NotNull Long> showIds) {
    }
}
