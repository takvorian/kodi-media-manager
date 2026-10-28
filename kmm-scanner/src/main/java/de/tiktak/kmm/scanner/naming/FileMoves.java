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
 * Verschiebt Dateien und Ordner und merkt sich jeden Schritt, damit sich alles zurücknehmen lässt, wenn ein
 * späterer Schritt fehlschlägt. Reine Änderungen der Groß- und Kleinschreibung laufen über einen Zwischennamen,
 * weil Windows und Netzlaufwerke sie sonst ablehnen.
 */
@Slf4j
public final class FileMoves {

    private static final String TEMPORARY_SUFFIX = ".kmm-rename";

    private final List<Undo> done = new ArrayList<>();

    /** Name für den Zwischenschritt beim Umbenennen. */
    public static Path temporary(Path file) {
        return file.resolveSibling(file.getFileName() + TEMPORARY_SUFFIX);
    }

    /**
     * @throws IOException wenn das Ziel existiert oder das Verschieben fehlschlägt
     */
    public void move(Path from, Path to) throws IOException {
        Files.move(from, to);
        done.add(() -> Files.move(to, from));
    }

    /**
     * Benennt einen Ordner um oder verschiebt ihn; fehlende Ordner auf dem Weg werden angelegt.
     *
     * @throws IOException wenn das Ziel schon existiert oder das Verschieben fehlschlägt
     */
    public void moveDirectory(Path current, Path target) throws IOException {
        if (sameIgnoringCase(current, target)) {
            Path temporary = temporary(current);
            move(current, temporary);
            move(temporary, target);
            return;
        }
        if (Files.exists(target)) {
            throw new IOException("Der Ordner „%s“ existiert bereits.".formatted(target));
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

    /** Nimmt alle Schritte in umgekehrter Reihenfolge zurück, soweit möglich. */
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
     * Entfernt den Ordner und leere Ordner darüber, bis zu {@code stop} ausschließlich; nicht leere bleiben.
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
