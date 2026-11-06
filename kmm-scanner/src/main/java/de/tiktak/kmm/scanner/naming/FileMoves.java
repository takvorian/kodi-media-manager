package de.tiktak.kmm.scanner.naming;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

/**
 * Moves files and folders and remembers every step so everything can be undone if a later step fails. Changes of
 * letter case only go through an intermediate name because Windows and network drives reject them otherwise.
 */
@Slf4j
public final class FileMoves {

    private static final String TEMPORARY_SUFFIX = ".kmm-rename";

    private final List<Undo> done = new ArrayList<>();

    /** Name for the intermediate step when renaming. */
    public static Path temporary(Path file) {
        return file.resolveSibling(file.getFileName() + TEMPORARY_SUFFIX);
    }

    /**
     * @throws IOException if the target exists or the move fails
     */
    public void move(Path from, Path to) throws IOException {
        Files.move(from, to);
        done.add(() -> Files.move(to, from));
    }

    /**
     * Renames or moves a folder; missing folders on the way are created.
     *
     * @throws IOException if the target already exists or the move fails
     */
    public void moveDirectory(Path current, Path target) throws IOException {
        if (sameIgnoringCase(current, target)) {
            Path temporary = temporary(current);
            move(current, temporary);
            move(temporary, target);
            return;
        }
        if (Files.exists(target)) {
            throw new FolderExistsException(target);
        }
        createDirectories(target.getParent());
        move(current, target);
    }

    public void createDirectories(Path directory) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (Path path = directory; path != null && !Files.exists(path); path = path.getParent()) {
            missing.addFirst(path);
        }
        for (Path path : missing) {
            Files.createDirectory(path);
            done.add(() -> Files.deleteIfExists(path));
        }
    }

    /** Undoes all steps in reverse order, as far as possible. */
    public void undo() {
        for (Undo step : done.reversed()) {
            try {
                step.run();
            } catch (IOException e) {
                log.error("Renaming could not be undone completely", e);
            }
        }
        done.clear();
    }

    /**
     * Removes the folder and empty folders above it, up to {@code stop} exclusively; non-empty ones are kept.
     */
    public static void removeEmptyDirectories(Path directory, Path stop) {
        Path root = stop.toAbsolutePath().normalize();
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

    public static boolean sameIgnoringCase(Path first, Path second) {
        return first.toString().toLowerCase(Locale.ROOT).equals(second.toString().toLowerCase(Locale.ROOT));
    }

    @FunctionalInterface
    private interface Undo {
        void run() throws IOException;
    }
}
