package de.tiktak.kmm.backup;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import de.tiktak.kmm.migration.ember.path.PathMapper;
import de.tiktak.kmm.migration.ember.path.PathMappingRule;

/**
 * Schreibt Pfade aus der Sicherung mit der Pfad-Zuordnung um. Pfade ohne passende Regel bleiben unverändert.
 */
final class BackupPaths {

    private final PathMapper mapper;
    private final boolean hasRules;
    private int unmapped;

    private BackupPaths(PathMapper mapper, boolean hasRules) {
        this.mapper = mapper;
        this.hasRules = hasRules;
    }

    /**
     * @throws BackupFileException wenn ein Ziel auf diesem System kein absoluter Pfad ist
     */
    static BackupPaths of(List<BackupImportRequest.PathMapping> mappings) {
        mappings.forEach(mapping -> requireAbsolute(mapping.to()));
        return new BackupPaths(new PathMapper(mappings.stream()
                .map(mapping -> new PathMappingRule(mapping.from(), mapping.to()))
                .toList()), !mappings.isEmpty());
    }

    String map(String path) {
        if (!hasRules) {
            return path;
        }
        Optional<String> mapped = mapper.map(path);
        if (mapped.isEmpty()) {
            unmapped++;
        }
        return mapped.map(BackupPaths::normalized).orElse(path);
    }

    private static String normalized(String target) {
        try {
            return Path.of(target).normalize().toString();
        } catch (InvalidPathException e) {
            throw new BackupFileException("„%s“ ist auf diesem System kein gültiger Pfad.".formatted(target), e);
        }
    }

    int unmapped() {
        return unmapped;
    }

    private static void requireAbsolute(String target) {
        try {
            if (Path.of(target).isAbsolute()) {
                return;
            }
        } catch (InvalidPathException e) {
            throw notAbsolute(target, e);
        }
        throw notAbsolute(target, null);
    }

    private static BackupFileException notAbsolute(String target, Throwable cause) {
        return new BackupFileException("„%s“ ist auf diesem System kein absoluter Pfad. Unter Docker beginnt er zum "
                .formatted(target) + "Beispiel mit /media.", cause);
    }
}
