package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import de.tiktak.kmm.scanner.naming.FileMoves;

/**
 * Carries out a rename plan in the file system. Files are renamed via an intermediate name, so that changes of
 * upper and lower case only and swapped names succeed as well. If a step fails, the previous ones are undone.
 * Empty former folders are removed up to the source.
 */
final class MovieFileMover {

    private MovieFileMover() {
    }

    /**
     * @param sourceRoot root of the source; it and the folders above it are never removed
     * @throws IOException if a step fails; the file system is then back in its old state, as far as possible
     */
    static Relocation move(MovieRenamePlan plan, Path sourceRoot) throws IOException {
        Path current = Path.of(plan.currentDirectory());
        Path target = Path.of(plan.targetDirectory());
        if (!Files.isDirectory(current)) {
            throw new LocalizedIOException(LocalizedText.of("movie.rename.folderGone").with("folder", current));
        }
        FileMoves moves = new FileMoves();
        try {
            renameFiles(current, plan.renames(), moves);
            if (!target.toString().equals(current.toString())) {
                moves.moveDirectory(current, target);
                FileMoves.removeEmptyDirectories(current.getParent(), sourceRoot);
            }
        } catch (IOException e) {
            moves.undo();
            throw e;
        }
        Map<String, String> names = new HashMap<>();
        plan.renames().forEach(rename -> names.put(rename.currentName(), rename.targetName()));
        return new Relocation(current, target, names);
    }

    private static void renameFiles(Path directory, List<FileRename> renames, FileMoves moves) throws IOException {
        for (FileRename rename : renames) {
            Path file = directory.resolve(rename.currentName());
            moves.move(file, FileMoves.temporary(file));
        }
        for (FileRename rename : renames) {
            Path target = directory.resolve(rename.targetName());
            if (Files.exists(target)) {
                throw new LocalizedIOException(LocalizedText.of("movie.rename.fileExists")
                        .with("file", target.getFileName()));
            }
            moves.move(FileMoves.temporary(directory.resolve(rename.currentName())), target);
        }
    }

    /**
     * Where the files of the movie went.
     *
     * @param names new file names per former name
     */
    record Relocation(Path previousDirectory, Path directory, Map<String, String> names) {

        Relocation {
            names = Map.copyOf(names);
        }

        /** @return the new location of a file of the movie, other paths unchanged */
        Path relocate(Path file) {
            if (!file.startsWith(previousDirectory)) {
                return file;
            }
            Path relative = previousDirectory.relativize(file);
            if (relative.getNameCount() == 1) {
                return directory.resolve(names.getOrDefault(relative.toString(), relative.toString()));
            }
            return directory.resolve(relative);
        }

        boolean changesDirectory() {
            return !previousDirectory.toString().equals(directory.toString());
        }
    }
}
