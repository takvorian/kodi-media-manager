package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of renaming several movies.
 *
 * @param renamed   renamed movies
 * @param unchanged movies that already had these names
 * @param skipped   movies with a conflict or without a valid name
 * @param failed    movies the file system refused to rename
 * @param problems  messages about skipped and failed movies, at most {@link MovieRenameService#MAX_LISTED_PROBLEMS}
 */
public record MovieRenameResult(long sourceId, int renamed, int unchanged, int skipped, int failed,
                                List<LocalizedText> problems) {

    public MovieRenameResult {
        problems = List.copyOf(problems);
    }
}
