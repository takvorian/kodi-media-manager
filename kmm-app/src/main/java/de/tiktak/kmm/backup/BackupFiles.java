package de.tiktak.kmm.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import de.tiktak.kmm.i18n.LocalizedText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the backup files under {@code ${kmm.home}/backup}. Exports and uploads may contain credentials; the next
 * start therefore deletes leftovers of an aborted run.
 */
@Slf4j
@Component
class BackupFiles {

    static final String DIRECTORY_NAME = "backup";

    private static final String EXPORT_PREFIX = "export-";
    private static final String EXPORT_SUFFIX = ".tmp";
    private static final String UPLOAD_NAME = "upload.json.gz";
    private static final String SAFETY_PREFIX = "vor-import-";
    private static final String SAFETY_SUFFIX = ".json.gz";
    private static final DateTimeFormatter SAFETY_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final int SAFETY_COPIES_KEPT = 3;

    private final Path directory;
    private final Clock clock;

    BackupFiles(@Value("${kmm.home}") Path home, Clock clock) {
        directory = home.resolve(DIRECTORY_NAME);
        this.clock = clock;
    }

    /** Replaces the uploaded file; there is only one at a time. */
    void storeUpload(MultipartFile file) {
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(UPLOAD_NAME));
        } catch (IOException e) {
            discardUpload();
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @throws BackupFileException if no file is uploaded
     */
    Path upload() {
        Path upload = directory.resolve(UPLOAD_NAME);
        if (!Files.isRegularFile(upload)) {
            throw new BackupFileException(LocalizedText.of("backup.uploadMissing"));
        }
        return upload;
    }

    void discardUpload() {
        try {
            Files.deleteIfExists(directory.resolve(UPLOAD_NAME));
        } catch (IOException e) {
            log.warn("Uploaded backup could not be deleted", e);
        }
    }

    /** File for the state before an import; {@link #pruneSafetyCopies()} cleans up older ones. */
    Path newSafetyCopy() {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String time = SAFETY_TIME.format(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
        return directory.resolve(SAFETY_PREFIX + time + SAFETY_SUFFIX);
    }

    /** The name contains the time, so the newest ones come last alphabetically. */
    void pruneSafetyCopies() {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> copies = files
                    .filter(file -> file.getFileName().toString().startsWith(SAFETY_PREFIX))
                    .sorted()
                    .toList();
            for (Path copy : copies.subList(0, Math.max(0, copies.size() - SAFETY_COPIES_KEPT))) {
                Files.deleteIfExists(copy);
            }
        } catch (IOException e) {
            log.warn("Old backup safety copies could not be deleted", e);
        }
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
        discardUpload();
        try (var files = Files.newDirectoryStream(directory, EXPORT_PREFIX + "*" + EXPORT_SUFFIX)) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("Leftover backup exports could not be deleted", e);
        }
    }
}
