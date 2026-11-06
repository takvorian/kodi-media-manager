package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * What renaming a movie would change.
 *
 * @param currentDirectory folder of the movie
 * @param targetDirectory  folder afterwards; equal to {@code currentDirectory} if it stays
 * @param renames          the files whose name changes, names without folder
 * @param message          reason for {@link Status#CONFLICT} and {@link Status#FAILED}, otherwise {@code null}
 */
public record MovieRenamePlan(long movieId, String title, String currentDirectory, String targetDirectory,
                              List<FileRename> renames, Status status, LocalizedText message) {

    public MovieRenamePlan {
        renames = List.copyOf(renames);
    }

    public enum Status {
        /** Folder and files already have these names. */
        UNCHANGED,
        /** Folder or files get new names. */
        RENAME,
        /** A target is taken already; the movie is skipped. */
        CONFLICT,
        /** The pattern yields no name or the folder cannot be read. */
        FAILED
    }

    /** A file in the folder of the movie, before and after. */
    public record FileRename(String currentName, String targetName) {
    }
}
