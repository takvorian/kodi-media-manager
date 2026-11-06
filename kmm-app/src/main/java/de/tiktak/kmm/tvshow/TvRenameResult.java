package de.tiktak.kmm.tvshow;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of renaming several TV shows.
 *
 * @param renamed   renamed TV shows
 * @param unchanged TV shows that already had these names
 * @param skipped   TV shows with a conflict or without a valid name
 * @param failed    TV shows the file system refused to rename
 * @param problems  messages about skipped TV shows and episode files, at most
 *                  {@link TvShowRenameService#MAX_LISTED_PROBLEMS}
 */
public record TvRenameResult(long sourceId, int renamed, int unchanged, int skipped, int failed,
                             List<LocalizedText> problems) {

    public TvRenameResult {
        problems = List.copyOf(problems);
    }
}
