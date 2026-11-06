package de.tiktak.kmm.tvshow;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Renaming of TV shows by patterns.
 */
@RestController
@RequiredArgsConstructor
public class TvShowRenameController {

    public static final String SOURCE_RENAME_PATH = "/api/v1/sources/{sourceId}/tv-rename";
    public static final String SOURCE_PREVIEW_PATH = SOURCE_RENAME_PATH + "/preview";
    public static final String SHOW_RENAME_PATH = "/api/v1/tvshows/{showId}/rename";
    public static final String SHOW_PREVIEW_PATH = SHOW_RENAME_PATH + "/preview";

    private final TvShowRenameService renameService;

    /** Shows what renaming would change, without changing anything. */
    @PostMapping(SOURCE_PREVIEW_PATH)
    public List<TvShowRenamePlan> preview(@PathVariable long sourceId, @Valid @RequestBody TvRenamePatterns patterns) {
        return renameService.preview(sourceId, patterns);
    }

    /** Renames the TV shows of the source in the background. */
    @PostMapping(SOURCE_RENAME_PATH)
    public ResponseEntity<JobSnapshot> rename(@PathVariable long sourceId, @Valid @RequestBody TvRenameRequest request) {
        JobSnapshot job = renameService.startSourceRename(sourceId, request);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @PostMapping(SHOW_PREVIEW_PATH)
    public TvShowRenamePlan previewShow(@PathVariable long showId, @Valid @RequestBody TvRenamePatterns patterns) {
        return renameService.previewShow(showId, patterns);
    }

    /** Renames a TV show immediately; conflicts and errors are in the result. */
    @PostMapping(SHOW_RENAME_PATH)
    public TvShowRenamePlan renameShow(@PathVariable long showId, @Valid @RequestBody TvRenamePatterns patterns) {
        return renameService.renameShow(showId, patterns);
    }
}
