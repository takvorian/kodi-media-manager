package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import lombok.extern.slf4j.Slf4j;

/**
 * Führt einen Umbenennungsplan im Dateisystem aus. Dateien werden über einen Zwischennamen umbenannt, damit
 * auch reine Änderungen der Groß- und Kleinschreibung und vertauschte Namen gelingen. Schlägt ein Schritt
 * fehl, werden die bisherigen zurückgenommen. Leere frühere Ordner werden bis zur Quelle entfernt.
 */
@Slf4j
final class MovieFileMover {

    private static final String TEMPORARY_SUFFIX = ".kmm-rename";

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
        List<Undo> done = new ArrayList<>();
        try {
            renameFiles(current, plan.renames(), done);
            if (!target.toString().equals(current.toString())) {
                moveDirectory(current, target, done);
                removeEmptyParents(current.getParent(), sourceRoot);
            }
        } catch (IOException e) {
            undo(done);
            throw e;
        }
        Map<String, String> names = new HashMap<>();
        plan.renames().forEach(rename -> names.put(rename.currentName(), rename.targetName()));
        return new Relocation(current, target, names);
    }

    private static void renameFiles(Path directory, List<FileRename> renames, List<Undo> done) throws IOException {
        for (FileRename rename : renames) {
            move(directory.resolve(rename.currentName()), directory.resolve(rename.currentName() + TEMPORARY_SUFFIX),
                    done);
        }
        for (FileRename rename : renames) {
            Path temporary = directory.resolve(rename.currentName() + TEMPORARY_SUFFIX);
            Path target = directory.resolve(rename.targetName());
            if (Files.exists(target)) {
                throw new IOException("Die Datei „%s“ existiert bereits.".formatted(target.getFileName()));
            }
            move(temporary, target, done);
        }
    }

    private static void moveDirectory(Path current, Path target, List<Undo> done) throws IOException {
        boolean onlyCase = current.toString().toLowerCase(Locale.ROOT).equals(target.toString().toLowerCase(Locale.ROOT));
        if (onlyCase) {
            Path temporary = current.resolveSibling(current.getFileName() + TEMPORARY_SUFFIX);
            move(current, temporary, done);
            move(temporary, target, done);
            return;
        }
        if (Files.exists(target)) {
            throw new IOException("Der Ordner „%s“ existiert bereits.".formatted(target));
        }
        createDirectories(target.getParent(), done);
        move(current, target, done);
    }

    private static void move(Path from, Path to, List<Undo> done) throws IOException {
        Files.move(from, to);
        done.add(() -> Files.move(to, from));
    }

    private static void createDirectories(Path directory, List<Undo> done) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (Path path = directory; path != null && !Files.exists(path); path = path.getParent()) {
            missing.addFirst(path);
        }
        for (Path path : missing) {
            Files.createDirectory(path);
            done.add(() -> Files.deleteIfExists(path));
        }
    }

    private static void removeEmptyParents(Path directory, Path sourceRoot) {
        Path root = sourceRoot.toAbsolutePath().normalize();
        for (Path path = directory; path != null && path.startsWith(root) && !path.equals(root);
                path = path.getParent()) {
            try {
                Files.delete(path);
            } catch (DirectoryNotEmptyException e) {
                return;
            } catch (IOException e) {
                log.warn("Empty folder {} not removed: {}", path, e.getMessage());
                return;
            }
        }
    }

    private static void undo(List<Undo> done) {
        for (Undo step : done.reversed()) {
            try {
                step.run();
            } catch (IOException e) {
                log.error("Renaming could not be undone completely", e);
            }
        }
    }

    @FunctionalInterface
    private interface Undo {
        void run() throws IOException;
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
