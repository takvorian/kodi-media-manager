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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Backs up the KMM data into a file and restores it. Media, NFOs and images are not included; the import writes
 * nothing into the media folders and does not contact Kodi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    public static final String JOB_KEY = "backup-import";
    static final String TITLE = "backup.import.title";
    static final String DRY_RUN_TITLE = "backup.import.dryRunTitle";

    private static final LocalizedText SAFETY_COPY_MESSAGE = LocalizedText.of("backup.import.safetyCopy");

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
     * Accepts the file and checks it. An unusable file is not kept.
     *
     * @throws BackupFileException if the file is damaged or comes from a newer KMM version
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException if a job is running
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
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException if a job is running
     */
    public void discardUpload() {
        jobService.runWhileIdle(files::discardUpload);
    }

    /**
     * Starts the import as a job alongside which no other runs. Before a real import a backup of the previous state is
     * written, afterwards the uploaded file is deleted. A dry run rolls back at the end and keeps it.
     *
     * @throws BackupFileException if no file is uploaded or the path mapping does not fit
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException if a job is running
     */
    public JobSnapshot startImport(BackupImportRequest request) {
        Path file = files.upload();
        BackupHeader header = readChecked(file);
        BackupPaths.of(request.pathMappings());
        LocalizedText title = LocalizedText.of(request.dryRun() ? DRY_RUN_TITLE : TITLE);
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
            throw new BackupFileException(LocalizedText.of("backup.newerVersionOf")
                    .with("version", header.applicationVersion()));
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
            throw new BackupFileException(LocalizedText.of("backup.importFailed")
                    .with("reason", e.getMostSpecificCause().getMessage()), e);
        }
        if (!request.dryRun()) {
            settingsService.reload();
            apiKeyService.reload();
        }
        log.info("Backup import{} finished in mode {}", request.dryRun() ? " (dry run)" : "", request.mode());
        return report;
    }

    /** Contains the credentials so that the state before the import stays completely restorable. */
    private String writeSafetyCopy(JobContext context) {
        context.reportProgress(0, 0, SAFETY_COPY_MESSAGE);
        Path copy = files.newSafetyCopy();
        write(copy, true);
        files.pruneSafetyCopies();
        return copy.toString();
    }

    /**
     * Writes the backup into a temporary file. While it is being written no job starts, so that the file shows a
     * consistent state.
     *
     * @param credentials whether API keys and passwords get into the file
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException if a job is running
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
