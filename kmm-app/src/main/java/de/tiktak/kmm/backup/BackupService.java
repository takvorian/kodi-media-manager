package de.tiktak.kmm.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sichert die KMM-Daten in eine Datei und stellt sie wieder her. Medien, NFOs und Bilder gehören nicht dazu; der Import
 * schreibt nichts in die Medienordner und spricht Kodi nicht an.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    public static final String JOB_KEY = "backup-import";

    private static final String SAFETY_COPY_MESSAGE = "Bisheriger Stand wird gesichert";

    private final JobService jobService;
    private final BackupWriter writer;
    private final BackupReader reader;
    private final BackupFiles files;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final Flyway flyway;
    private final SettingsService settingsService;
    private final ApiKeyService apiKeyService;

    /**
     * Nimmt die Datei an und prüft sie. Eine unbrauchbare Datei bleibt nicht liegen.
     *
     * @throws BackupFileException wenn die Datei beschädigt ist oder aus einer neueren KMM-Version stammt
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException wenn gerade ein Job läuft
     */
    public BackupHeader upload(MultipartFile file) {
        jobService.runWhileIdle(() -> files.storeUpload(file));
        try {
            return readChecked(files.upload());
        } catch (RuntimeException e) {
            files.discardUpload();
            throw e;
        }
    }

    /**
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException wenn gerade ein Job läuft
     */
    public void discardUpload() {
        jobService.runWhileIdle(files::discardUpload);
    }

    /**
     * Startet den Import als Job, neben dem kein anderer läuft. Vor einem echten Import entsteht eine Sicherung des
     * bisherigen Stands, danach wird die hochgeladene Datei gelöscht. Ein Probelauf rollt am Ende zurück und behält sie.
     *
     * @throws BackupFileException wenn keine Datei hochgeladen ist oder die Pfad-Zuordnung nicht passt
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException wenn gerade ein Job läuft
     */
    public JobSnapshot startImport(BackupImportRequest request) {
        Path file = files.upload();
        BackupHeader header = readChecked(file);
        BackupPaths.of(request.pathMappings());
        String title = request.dryRun() ? "Sicherung einspielen (Probelauf)" : "Sicherung einspielen";
        return jobService.submitExclusive(JOB_KEY, title, context -> {
            try {
                return runImport(file, header, request, context);
            } finally {
                if (!request.dryRun()) {
                    files.discardUpload();
                }
            }
        });
    }

    private BackupHeader readChecked(Path file) {
        BackupHeader header = reader.readHeader(file);
        MigrationVersion current = flyway.info().current().getVersion();
        if (MigrationVersion.fromVersion(header.schemaVersion()).compareTo(current) > 0) {
            throw new BackupFileException("Die Sicherung stammt aus einer neueren KMM-Version (%s). Bitte aktualisiere "
                    .formatted(header.applicationVersion()) + "KMM zuerst.");
        }
        return header;
    }

    private BackupImportReport runImport(Path file, BackupHeader header, BackupImportRequest request,
            JobContext context) {
        String safetyCopy = request.dryRun() ? null : writeSafetyCopy(context);
        BackupImportReport report;
        try {
            report = transactions.execute(status -> {
                BackupImportRun run = new BackupImportRun(jdbcTemplate, header, request, context);
                run.prepare();
                reader.readRows(file, run::accept);
                BackupImportReport result = run.finish(safetyCopy);
                if (request.dryRun()) {
                    status.setRollbackOnly();
                }
                return result;
            });
        } catch (DataAccessException e) {
            log.warn("Backup import failed", e);
            throw new BackupFileException("Der Import ist fehlgeschlagen, es wurde nichts geändert: "
                    + e.getMostSpecificCause().getMessage(), e);
        }
        if (!request.dryRun()) {
            settingsService.reload();
            apiKeyService.reload();
        }
        log.info("Backup import{} finished in mode {}", request.dryRun() ? " (dry run)" : "", request.mode());
        return report;
    }

    /** Enthält die Zugangsdaten, damit der Stand vor dem Import vollständig wiederherstellbar bleibt. */
    private String writeSafetyCopy(JobContext context) {
        context.reportProgress(0, 0, SAFETY_COPY_MESSAGE);
        Path copy = files.newSafetyCopy();
        write(copy, true);
        files.pruneSafetyCopies();
        return copy.toString();
    }

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
