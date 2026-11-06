package de.tiktak.kmm.autoscrape;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.tvshow.TvShowController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Starts automatic scraping in the background, for a source, a single entry or a selection.
 * Progress and result are provided by the job API.
 */
@RestController
@RequiredArgsConstructor
public class AutoScrapeController {

    public static final String AUTO_SCRAPE_PATH = "/api/v1/sources/{sourceId}/auto-scrape";
    public static final String MOVIE_PATH = MovieController.MOVIE_PATH + "/auto-scrape";
    public static final String MOVIES_PATH = MovieController.MOVIES_PATH + "/auto-scrape";
    public static final String TV_SHOW_PATH = TvShowController.SHOW_PATH + "/auto-scrape";
    public static final String TV_SEASON_PATH = TvShowController.SHOW_PATH + "/seasons/{season}/auto-scrape";
    public static final String TV_SHOWS_PATH = TvShowController.SHOWS_PATH + "/auto-scrape";
    static final int MAX_ENTRIES = 20_000;

    private final AutoScrapeService autoScrapeService;

    @PostMapping(AUTO_SCRAPE_PATH)
    public ResponseEntity<JobSnapshot> start(@PathVariable long sourceId) {
        return accepted(autoScrapeService.start(sourceId));
    }

    @PostMapping(MOVIE_PATH)
    public ResponseEntity<JobSnapshot> startMovie(@PathVariable long movieId) {
        return accepted(autoScrapeService.startMovie(movieId));
    }

    @PostMapping(MOVIES_PATH)
    public ResponseEntity<JobSnapshot> startMovies(@Valid @RequestBody Movies movies) {
        return accepted(autoScrapeService.startMovies(movies.movieIds()));
    }

    @PostMapping(TV_SHOW_PATH)
    public ResponseEntity<JobSnapshot> startTvShow(@PathVariable long showId) {
        return accepted(autoScrapeService.startTvShow(showId));
    }

    /** @param overwrite whether episode titles and texts are replaced by those of the provider */
    @PostMapping(TV_SEASON_PATH)
    public ResponseEntity<JobSnapshot> startTvSeason(@PathVariable long showId, @PathVariable @PositiveOrZero int season,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        return accepted(autoScrapeService.startTvSeason(showId, season, overwrite));
    }

    @PostMapping(TV_SHOWS_PATH)
    public ResponseEntity<JobSnapshot> startTvShows(@Valid @RequestBody TvShows shows) {
        return accepted(autoScrapeService.startTvShows(shows.showIds()));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Movies(@NotNull @NotEmpty @Size(max = MAX_ENTRIES) List<@NotNull Long> movieIds) {
    }

    public record TvShows(@NotNull @NotEmpty @Size(max = MAX_ENTRIES) List<@NotNull Long> showIds) {
    }
}
