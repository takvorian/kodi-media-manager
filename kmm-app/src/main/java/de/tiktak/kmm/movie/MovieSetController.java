package de.tiktak.kmm.movie;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Filmset-Manager.
 */
@RestController
@RequestMapping(MovieSetController.BASE_PATH)
@RequiredArgsConstructor
public class MovieSetController {

    public static final String BASE_PATH = "/api/v1/moviesets";
    public static final String SET_PATH = "/{setId}";
    public static final String SEARCH_PATH = "/search";
    public static final String PROPOSAL_PATH = SET_PATH + "/proposal";
    public static final String COMPLETE_PATH = "/complete";

    private final MovieSetService setService;
    private final MovieSetJobService jobService;
    private final MovieSetScrapeService scrapeService;

    @GetMapping
    public List<MovieSetSummary> findAll() {
        return setService.findAll();
    }

    /** Sucht Sammlungen beim Online-Dienst. */
    @GetMapping(SEARCH_PATH)
    public List<ScrapeCandidate> search(@RequestParam @NotBlank String title) {
        return scrapeService.search(title);
    }

    /** Titel, Beschreibung und TMDB-ID eines Treffers als Vorschlag; gespeichert wird erst mit {@code PUT}. */
    @GetMapping(PROPOSAL_PATH)
    public MovieSetData proposal(@PathVariable long setId, @RequestParam @NotBlank String providerId) {
        return scrapeService.proposal(setId, providerId);
    }

    /** Ergänzt fehlende Beschreibungen und Bilder aller Sets als Job. */
    @PostMapping(COMPLETE_PATH)
    public ResponseEntity<JobSnapshot> completeAll() {
        return accepted(jobService.completeAll());
    }

    @GetMapping(SET_PATH)
    public MovieSetDetails find(@PathVariable long setId) {
        return setService.find(setId);
    }

    @PostMapping
    public ResponseEntity<MovieSetDetails> create(@Valid @RequestBody MovieSetData data) {
        MovieSetDetails created = setService.create(data);
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create(BASE_PATH + "/" + created.id()))
                .body(created);
    }

    /** Speichert die Angaben; die NFOs der Filme schreibt ein Job neu. */
    @PutMapping(SET_PATH)
    public ResponseEntity<JobSnapshot> update(@PathVariable long setId, @Valid @RequestBody MovieSetData data) {
        return accepted(jobService.update(setId, data));
    }

    @DeleteMapping(SET_PATH)
    public ResponseEntity<JobSnapshot> delete(@PathVariable long setId) {
        return accepted(jobService.delete(setId));
    }

    @PostMapping(SET_PATH + "/movies")
    public ResponseEntity<JobSnapshot> addMovies(@PathVariable long setId, @Valid @RequestBody Movies movies) {
        return accepted(jobService.addMovies(setId, movies.movieIds()));
    }

    @PostMapping(SET_PATH + "/movies/remove")
    public ResponseEntity<JobSnapshot> removeMovies(@PathVariable long setId, @Valid @RequestBody Movies movies) {
        return accepted(jobService.removeMovies(setId, movies.movieIds()));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Movies(@NotNull List<Long> movieIds) {
    }
}
