package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import de.tiktak.kmm.scanner.naming.FileMoves;

/**
 * Führt einen Umbenennungsplan im Dateisystem aus. Dateien werden über einen Zwischennamen umbenannt, damit
 * auch reine Änderungen der Groß- und Kleinschreibung und vertauschte Namen gelingen. Schlägt ein Schritt
 * fehl, werden die bisherigen zurückgenommen. Leere frühere Ordner werden bis zur Quelle entfernt.
 */
final class MovieFileMover {

    private MovieFileMover() {
    }

    /**
     * @param sourceRoot Wurzel der Quelle; sie und Ordner darüber werden nie entfernt
     * @throws IOException wenn ein Schritt fehlschlägt; das Dateisystem ist dann wieder im alten Zustand,
     *                     soweit das möglich war
     */
    static Relocation move(MovieRenamePlan plan, Path sourceRoot) throws IOException {
        Path current = Path.of(plan.currentDirectory());
        Path target = Path.of(plan.targetDirectory());
        if (!Files.isDirectory(current)) {
            throw new IOException("Der Ordner „%s“ existiert nicht mehr.".formatted(current));
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
                throw new IOException("Die Datei „%s“ existiert bereits.".formatted(target.getFileName()));
            }
            moves.move(FileMoves.temporary(directory.resolve(rename.currentName())), target);
        }
    }

    /**
     * Wohin Dateien des Films gewandert sind.
     *
     * @param names neue Dateinamen je bisherigem Namen
     */
    record Relocation(Path previousDirectory, Path directory, Map<String, String> names) {

        Relocation {
            names = Map.copyOf(names);
        }

        /** @return der neue Ort einer Datei des Films, andere Pfade unverändert */
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
