package de.tiktak.kmm.movie;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Umbenennen von Filmen nach Mustern.
 */
@RestController
@RequiredArgsConstructor
public class MovieRenameController {

    public static final String SOURCE_RENAME_PATH = "/api/v1/sources/{sourceId}/movie-rename";
    public static final String SOURCE_PREVIEW_PATH = SOURCE_RENAME_PATH + "/preview";

    private final MovieRenameService renameService;

    /** Zeigt, was das Umbenennen ändern würde, ohne etwas zu ändern. */
    @PostMapping(SOURCE_PREVIEW_PATH)
    public List<MovieRenamePlan> preview(@PathVariable long sourceId, @Valid @RequestBody MovieRenamePatterns patterns) {
        return renameService.preview(sourceId, patterns);
    }
}
