package de.tiktak.kmm.movie;

import java.net.URI;
import java.util.List;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MovieController {

    public static final String MOVIES_PATH = "/api/v1/movies";
    public static final String MOVIE_PATH = MOVIES_PATH + "/{movieId}";
    public static final String METADATA_PATH = MOVIE_PATH + "/metadata";
    public static final String QUERY_PATH = MOVIES_PATH + "/query";
    public static final String FILTER_VALUES_PATH = MOVIES_PATH + "/filter-values";
    public static final String BATCH_PATH = MOVIES_PATH + "/batch";
    public static final String QUERY_IDS_PATH = QUERY_PATH + "/ids";
    static final int DEFAULT_PAGE_SIZE = 50;
    static final String INVALID_SORT_TITLE = "Ungültige Sortierung";

    private final MovieQueryService queryService;
    private final MovieMetadataService metadataService;
    private final MovieEditService editService;
    private final MovieBatchService batchService;

    @GetMapping(MOVIES_PATH)
    public MoviePage findMovies(
            @RequestParam(required = false) String search,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return MoviePage.from(queryService.findMovies(search, pageable));
    }

    /** Filmliste mit Filter; ändert nichts, braucht aber einen Rumpf für die Bedingungen. */
    @PostMapping(QUERY_PATH)
    public MoviePage queryMovies(
            @Valid @RequestBody MovieFilter filter,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return MoviePage.from(queryService.findFiltered(filter, pageable));
    }

    /** IDs aller Filme, auf die der Filter passt, etwa um alle gefilterten Filme zu bearbeiten. */
    @PostMapping(QUERY_IDS_PATH)
    public List<Long> queryMovieIds(@Valid @RequestBody MovieFilter filter) {
        return queryService.findMovieIds(filter);
    }

    /** Stapel-Bearbeitung als Job; die NFOs der Filme werden geschrieben, umbenannt wird nicht. */
    @PostMapping(BATCH_PATH)
    public ResponseEntity<JobSnapshot> batchEdit(@Valid @RequestBody MovieBatchEdit edit) {
        JobSnapshot job = batchService.start(edit);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @GetMapping(FILTER_VALUES_PATH)
    public MovieFilterValues filterValues() {
        return queryService.filterValues();
    }

    @GetMapping(MOVIE_PATH)
    public MovieDetails findMovie(@PathVariable long movieId) {
        return metadataService.findDetails(movieId);
    }

    /**
     * Speichert die Metadaten und schreibt sie in die NFO des Films; je nach Einstellung wird er danach umbenannt.
     */
    @PutMapping(METADATA_PATH)
    public MovieDetails updateMetadata(@PathVariable long movieId, @Valid @RequestBody MovieMetadata metadata) {
        return editService.save(movieId, metadata);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Nach '%s' kann nicht sortiert werden.".formatted(exception.getPropertyName()));
        problem.setTitle(INVALID_SORT_TITLE);
        return problem;
    }
}
