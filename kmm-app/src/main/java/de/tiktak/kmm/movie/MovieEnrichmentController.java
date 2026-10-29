package de.tiktak.kmm.movie;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Bewertungen und leere Felder über die Ergänzungs-Scraper (etwa Trakt, OMDb) für die ausgewählten Filme.
 */
@RestController
@RequiredArgsConstructor
public class MovieEnrichmentController {

    public static final String REFRESH_PATH = MovieController.MOVIES_PATH + "/ratings/refresh";
    static final int MAX_ENTRIES = 20_000;

    private final MovieBatchService batchService;

    /** Aktualisiert die Bewertungen der Auswahl und füllt leere Felder, als Job. */
    @PostMapping(REFRESH_PATH)
    public ResponseEntity<JobSnapshot> refresh(@Valid @RequestBody Selection selection) {
        JobSnapshot job = batchService.startEnrichment(selection.movieIds());
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Selection(@NotNull @NotEmpty @Size(max = MAX_ENTRIES) List<@NotNull Long> movieIds) {
    }
}
