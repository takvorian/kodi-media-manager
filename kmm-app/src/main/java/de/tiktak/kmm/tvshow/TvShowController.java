package de.tiktak.kmm.tvshow;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TvShowController {

    public static final String SHOWS_PATH = "/api/v1/tvshows";
    public static final String SHOW_PATH = SHOWS_PATH + "/{showId}";
    public static final String SHOW_METADATA_PATH = SHOW_PATH + "/metadata";
    public static final String EPISODE_PATH = "/api/v1/episodes/{episodeId}";
    public static final String EPISODE_METADATA_PATH = EPISODE_PATH + "/metadata";
    static final int DEFAULT_PAGE_SIZE = 50;

    private final TvShowQueryService queryService;
    private final TvShowMetadataService metadataService;
    private final TvShowEditService editService;

    @GetMapping(SHOWS_PATH)
    public TvShowPage findShows(
            @RequestParam(required = false) String search,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return TvShowPage.from(queryService.findShows(search, pageable));
    }

    @GetMapping(SHOW_PATH)
    public TvShowDetails findShow(@PathVariable long showId) {
        return metadataService.findShow(showId);
    }

    /** Speichert die Metadaten und schreibt sie in die {@code tvshow.nfo}. */
    @PutMapping(SHOW_METADATA_PATH)
    public TvShowDetails updateShow(@PathVariable long showId, @Valid @RequestBody TvShowMetadata metadata) {
        return editService.saveShow(showId, metadata);
    }

    @GetMapping(EPISODE_PATH)
    public EpisodeDetails findEpisode(@PathVariable long episodeId) {
        return metadataService.findEpisode(episodeId);
    }

    /** Speichert die Metadaten und schreibt sie in die NFO der Episode. */
    @PutMapping(EPISODE_METADATA_PATH)
    public EpisodeDetails updateEpisode(@PathVariable long episodeId, @Valid @RequestBody EpisodeMetadata metadata) {
        return editService.saveEpisode(episodeId, metadata);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Nach '%s' kann nicht sortiert werden.".formatted(exception.getPropertyName()));
        problem.setTitle("Ungültige Sortierung");
        return problem;
    }
}
