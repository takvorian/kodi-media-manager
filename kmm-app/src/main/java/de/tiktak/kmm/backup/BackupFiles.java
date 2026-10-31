package de.tiktak.kmm.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Verwaltet die Dateien der Sicherung unter {@code ${kmm.home}/backup}. Exporte können Zugangsdaten enthalten; Reste
 * eines abgebrochenen Downloads löscht deshalb der nächste Start.
 */
@Slf4j
@Component
class BackupFiles {

    static final String DIRECTORY_NAME = "backup";

    private static final String EXPORT_PREFIX = "export-";
    private static final String EXPORT_SUFFIX = ".tmp";

    private final Path directory;

    BackupFiles(@Value("${kmm.home}") Path home) {
        directory = home.resolve(DIRECTORY_NAME);
    }

    Path newExportFile() {
        try {
            Files.createDirectories(directory);
            return Files.createTempFile(directory, EXPORT_PREFIX, EXPORT_SUFFIX);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void discardLeftovers() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.newDirectoryStream(directory, EXPORT_PREFIX + "*" + EXPORT_SUFFIX)) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("Leftover backup exports could not be deleted", e);
        }
    }
}
