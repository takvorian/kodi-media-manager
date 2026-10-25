package de.tiktak.kmm.movie;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MovieController {

    public static final String MOVIES_PATH = "/api/v1/movies";
    public static final String MOVIE_PATH = MOVIES_PATH + "/{movieId}";
    public static final String METADATA_PATH = MOVIE_PATH + "/metadata";
    public static final String SCAN_PATH = "/api/v1/sources/{sourceId}/scan";
    static final int DEFAULT_PAGE_SIZE = 50;
    static final String INVALID_SORT_TITLE = "Ungültige Sortierung";

    private final MovieQueryService queryService;
    private final MovieScanService scanService;
    private final MovieMetadataService metadataService;

    @GetMapping(MOVIES_PATH)
    public MoviePage findMovies(
            @RequestParam(required = false) String search,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return MoviePage.from(queryService.findMovies(search, pageable));
    }

    @GetMapping(MOVIE_PATH)
    public MovieDetails findMovie(@PathVariable long movieId) {
        return metadataService.findDetails(movieId);
    }

    /**
     * Speichert die Metadaten und schreibt sie in die NFO des Films.
     */
    @PutMapping(METADATA_PATH)
    public MovieDetails updateMetadata(@PathVariable long movieId, @Valid @RequestBody MovieMetadata metadata) {
        return metadataService.update(movieId, metadata);
    }

    @PostMapping(SCAN_PATH)
    public ScanResult scan(@PathVariable long sourceId) {
        return scanService.scan(sourceId);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Nach '%s' kann nicht sortiert werden.".formatted(exception.getPropertyName()));
        problem.setTitle(INVALID_SORT_TITLE);
        return problem;
    }
}
