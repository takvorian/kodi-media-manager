package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.FileMoves;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.EpisodeFile;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.FileRename;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;

/**
 * Carries out the rename plan of a TV show in the file system: first the episode files into their season folders,
 * then the show folder. Files go through temporary names; if a step fails, all of them are undone. Season folders
 * that became empty and former folders up to the source are removed.
 */
final class TvShowFileMover {

    private TvShowFileMover() {
    }

    /**
     * @param plan       a plan with status {@link Status#RENAME}; episode files with a conflict stay
     * @param sourceRoot root of the source; it and folders above it are never removed
     * @throws IOException if a step fails; the file system is then back in its old state as far as possible
     */
    static Relocation move(TvShowRenamePlan plan, Path sourceRoot) throws IOException {
        Path current = Path.of(plan.currentDirectory());
        Path target = Path.of(plan.targetDirectory());
        if (!Files.isDirectory(current)) {
            throw new LocalizedIOException(LocalizedText.of("tvShow.rename.folderGone").with("folder", current));
        }
        FileMoves moves = new FileMoves();
        Map<Path, Path> files = new HashMap<>();
        Set<Path> emptied = new LinkedHashSet<>();
        try {
            for (EpisodeFile file : plan.files()) {
                if (file.status() == Status.RENAME) {
                    moveEpisodeFile(current, file, moves, files);
                    emptied.add(current.resolve(file.currentFolder()));
                }
            }
            emptied.forEach(folder -> FileMoves.removeEmptyDirectories(folder, current));
            if (!target.toString().equals(current.toString())) {
                moves.moveDirectory(current, target);
                FileMoves.removeEmptyDirectories(current.getParent(), sourceRoot);
            }
        } catch (IOException e) {
            moves.undo();
            throw e;
        }
        return new Relocation(current, target, files);
    }

    private static void moveEpisodeFile(Path show, EpisodeFile file, FileMoves moves, Map<Path, Path> files)
            throws IOException {
        Path source = show.resolve(file.currentFolder());
        Path target = show.resolve(file.targetFolder());
        moves.createDirectories(target);
        for (FileRename rename : file.renames()) {
            Path from = source.resolve(rename.currentName());
            moves.move(from, FileMoves.temporary(from));
        }
        for (FileRename rename : file.renames()) {
            Path from = source.resolve(rename.currentName());
            Path to = target.resolve(rename.targetName());
            if (Files.exists(to)) {
                throw new LocalizedIOException(LocalizedText.of("tvShow.rename.fileExists").with("file", to));
            }
            moves.move(FileMoves.temporary(from), to);
            files.put(from, to);
        }
    }

    /**
     * Where files of the TV show have moved to.
     *
     * @param files new location per previous location, both in the previous show folder
     */
    record Relocation(Path previousDirectory, Path directory, Map<Path, Path> files) {

        Relocation {
            files = Map.copyOf(files);
        }

        /** @return the new location of a file of the TV show, other paths unchanged */
        Path relocate(Path file) {
            Path moved = files.getOrDefault(file, file);
            return moved.startsWith(previousDirectory) ? directory.resolve(previousDirectory.relativize(moved)) : moved;
        }
    }
}
