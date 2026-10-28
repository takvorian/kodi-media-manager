package de.tiktak.kmm.movie;

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
 * Umbenennen von Filmen nach Mustern.
 */
@RestController
@RequiredArgsConstructor
public class MovieRenameController {

    public static final String SOURCE_RENAME_PATH = "/api/v1/sources/{sourceId}/movie-rename";
    public static final String SOURCE_PREVIEW_PATH = SOURCE_RENAME_PATH + "/preview";
    public static final String MOVIE_RENAME_PATH = MovieController.MOVIE_PATH + "/rename";
    public static final String MOVIE_PREVIEW_PATH = MOVIE_RENAME_PATH + "/preview";

    private final MovieRenameService renameService;

    /** Zeigt, was das Umbenennen ändern würde, ohne etwas zu ändern. */
    @PostMapping(SOURCE_PREVIEW_PATH)
    public List<MovieRenamePlan> preview(@PathVariable long sourceId, @Valid @RequestBody MovieRenamePatterns patterns) {
        return renameService.preview(sourceId, patterns);
    }

    /** Benennt die Filme der Quelle im Hintergrund um. */
    @PostMapping(SOURCE_RENAME_PATH)
    public ResponseEntity<JobSnapshot> rename(@PathVariable long sourceId, @Valid @RequestBody MovieRenameRequest request) {
        JobSnapshot job = renameService.startSourceRename(sourceId, request);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @PostMapping(MOVIE_PREVIEW_PATH)
    public MovieRenamePlan previewMovie(@PathVariable long movieId, @Valid @RequestBody MovieRenamePatterns patterns) {
        return renameService.previewMovie(movieId, patterns);
    }

    /** Benennt einen Film sofort um; Konflikte und Fehler stehen im Ergebnis. */
    @PostMapping(MOVIE_RENAME_PATH)
    public MovieRenamePlan renameMovie(@PathVariable long movieId, @Valid @RequestBody MovieRenamePatterns patterns) {
        return renameService.renameMovie(movieId, patterns);
    }
}
