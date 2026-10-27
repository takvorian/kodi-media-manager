package de.tiktak.kmm.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Erkennt mehrteilige Filme anhand des ersten Teils, z. B. bei der Übernahme aus anderen Programmen.
 */
public final class StackedFiles {

    /** Mindestzahl der Teile, wenn die weiteren Teile nicht gezählt werden können. */
    static final int ASSUMED_PARTS = 2;
    private static final char EXTENSION_SEPARATOR = '.';

    private StackedFiles() {
    }

    /**
     * @return 1 für eine normale Datei, sonst die Zahl der vorhandenen Teile; ist der Ordner nicht
     *         lesbar, wird ein mehrteiliger Film mit {@value #ASSUMED_PARTS} Teilen angenommen
     */
    public static int partCount(Path file) {
        Optional<StackedFile> stacked = stackedFile(file);
        if (stacked.isEmpty()) {
            return 1;
        }
        try (Stream<Path> siblings = Files.list(file.getParent())) {
            long parts = siblings.map(StackedFiles::stackedFile)
                    .flatMap(Optional::stream)
                    .filter(part -> part.stackKey().equals(stacked.get().stackKey()))
                    .count();
            return (int) Math.max(parts, ASSUMED_PARTS);
        } catch (IOException | RuntimeException e) {
            return ASSUMED_PARTS;
        }
    }

    /**
     * @return alle vorhandenen Teile in der Reihenfolge ihrer Nummern; für eine normale Datei nur diese
     * @throws UncheckedIOException wenn der Ordner eines mehrteiligen Films nicht lesbar ist
     */
    public static List<Path> parts(Path file) {
        Optional<StackedFile> stacked = stackedFile(file);
        if (stacked.isEmpty()) {
            return List.of(file);
        }
        try (Stream<Path> siblings = Files.list(file.getParent())) {
            return siblings.flatMap(sibling -> stackedFile(sibling).stream()
                            .filter(part -> part.stackKey().equals(stacked.get().stackKey()))
                            .map(part -> new NumberedPart(sibling, part.partNumber())))
                    .sorted(Comparator.comparingInt(NumberedPart::number))
                    .map(NumberedPart::file)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<StackedFile> stackedFile(Path file) {
        String fileName = file.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        if (extensionStart <= 0) {
            return Optional.empty();
        }
        return StackedFile.of(file, fileName.substring(0, extensionStart), fileName.substring(extensionStart + 1));
    }

    private record NumberedPart(Path file, int number) {
    }
}
