package de.tiktak.kmm.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.job.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sichert die KMM-Daten in eine Datei. Medien, NFOs und Bilder gehören nicht dazu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final JobService jobService;
    private final BackupWriter writer;
    private final BackupFiles files;

    /**
     * Schreibt die Sicherung in eine temporäre Datei. Solange sie entsteht, startet kein Job, damit die Datei einen
     * einheitlichen Stand zeigt.
     *
     * @param credentials ob API-Keys und Passwörter in die Datei gelangen
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException wenn gerade ein Job läuft
     */
    public Path export(boolean credentials) {
        Path file = files.newExportFile();
        try {
            jobService.runWhileIdle(() -> write(file, credentials));
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
        log.info("Backup exported, credentials {}", credentials ? "included" : "left out");
        return file;
    }

    private void write(Path file, boolean credentials) {
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(file))) {
            writer.write(output, credentials);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Backup file {} could not be deleted", file, e);
        }
    }
}
