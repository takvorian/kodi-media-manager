package de.tiktak.kmm.tvshow;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * What renaming a TV show would change: the show folder and, per episode file, season folder and names.
 *
 * @param currentDirectory folder of the TV show
 * @param targetDirectory  folder afterwards; equal to {@code currentDirectory} if it stays
 * @param files            the episode files that change or cannot be renamed
 * @param message          reason for {@link Status#CONFLICT} and {@link Status#FAILED}, otherwise {@code null}
 */
public record TvShowRenamePlan(long showId, String title, String currentDirectory, String targetDirectory,
                               List<EpisodeFile> files, Status status, LocalizedText message) {

    public TvShowRenamePlan {
        files = List.copyOf(files);
    }

    /** Whether something can be renamed: the show folder or at least one episode file. */
    public boolean hasChanges() {
        return !currentDirectory.equals(targetDirectory)
                || files.stream().anyMatch(file -> file.status() == Status.RENAME);
    }

    public enum Status {
        /** Folders and files already have these names. */
        UNCHANGED,
        /** Folders or files get new names; episode files with a conflict stay. */
        RENAME,
        /** A target is already taken; the TV show or episode file is skipped. */
        CONFLICT,
        /** A pattern yields no name or a folder cannot be read. */
        FAILED
    }

    /**
     * An episode file with its companion files.
     *
     * @param episodes      e.g. {@code S01E01E02}
     * @param currentFolder folder relative to the show folder, empty for the show folder itself
     * @param targetFolder  folder afterwards, relative to the show folder
     * @param renames       video and companion files, before and after
     * @param status        {@link Status#RENAME} or {@link Status#CONFLICT} or {@link Status#FAILED}
     * @param message       reason for {@link Status#CONFLICT} and {@link Status#FAILED}, otherwise {@code null}
     */
    public record EpisodeFile(String episodes, String currentFolder, String targetFolder, List<FileRename> renames,
                              Status status, LocalizedText message) {

        public EpisodeFile {
            renames = List.copyOf(renames);
        }
    }

    /** A file, before and after, without folder. */
    public record FileRename(String currentName, String targetName) {
    }
}
