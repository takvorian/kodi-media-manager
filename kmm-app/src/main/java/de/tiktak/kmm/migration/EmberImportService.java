package de.tiktak.kmm.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileDetector;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.EmberProfileInspector;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHostsReader;
import de.tiktak.kmm.migration.ember.library.EmberLibrary;
import de.tiktak.kmm.migration.ember.library.EmberLibraryReader;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.library.EmberTvLibrary;
import de.tiktak.kmm.migration.ember.library.EmberTvLibraryReader;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsException;
import de.tiktak.kmm.migration.ember.settings.EmberAdvancedSettingsReader;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Analyzes Ember profiles and imports their movie and TV show library as a background job. A dry run performs the
 * import completely and rolls it back at the end; so its report shows exactly what the real import would change.
 * Importing again updates instead of duplicating.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmberImportService {

    public static final String JOB_KEY = "ember-import";
    static final String TITLE = "migration.import.title";
    static final String DRY_RUN_TITLE = "migration.import.dryRunTitle";
    static final LocalizedText READING_MESSAGE = LocalizedText.of("migration.import.reading");

    private final EmberProfileInspector profileInspector;
    private final EmberProfileDetector profileDetector;
    private final EmberLibraryReader libraryReader;
    private final EmberTvLibraryReader tvLibraryReader;
    private final EmberSettingsReader settingsReader;
    private final EmberAdvancedSettingsReader advancedSettingsReader;
    private final EmberKodiHostsReader kodiHostsReader;
    private final EmberLibraryImporter importer;
    private final EmberKodiHostImporter kodiHostImporter;
    private final JobService jobService;
    private final TransactionTemplate transactionTemplate;
    private final EmberProfileUpload profileUpload;

    /**
     * Accepts the uploaded profile and analyzes it. An unusable profile is not kept.
     *
     * @throws EmberProfileUploadException if the files do not fit or an import is running
     * @throws de.tiktak.kmm.migration.ember.EmberProfileException if the profile is invalid
     */
    public EmberInspectionResponse upload(List<MultipartFile> files) {
        requireNoRunningImport();
        profileUpload.store(files);
        try {
            return inspect(profileUpload.directory());
        } catch (RuntimeException e) {
            profileUpload.discard();
            throw e;
        }
    }

    /**
     * Discards the uploaded profile.
     *
     * @throws EmberProfileUploadException if an import is running
     */
    public void discardUpload() {
        requireNoRunningImport();
        profileUpload.discard();
    }

    EmberInspectionResponse inspect(Path directory) {
        var inspection = profileInspector.inspect(directory);
        Path databaseFile = inspection.profile().databaseFile();
        List<EmberMovieSource> sources = libraryReader.readSources(databaseFile);
        return EmberInspectionResponse.from(inspection, sources, tvLibraryReader.readSources(databaseFile),
                inspectKodiHosts(inspection.profile()));
    }

    /**
     * Checks the uploaded profile and starts the import. After the real import it is deleted, even if the import
     * fails or is cancelled; a dry run keeps it for the import afterwards.
     *
     * @throws EmberProfileUploadException if no profile is uploaded
     * @throws de.tiktak.kmm.migration.ember.EmberProfileException if the profile is invalid
     */
    public JobSnapshot start(EmberImportRequest request) {
        EmberProfile profile = profileDetector.detect(profileUpload.directory());
        LocalizedText title = LocalizedText.of(request.dryRun() ? DRY_RUN_TITLE : TITLE);
        return jobService.submit(JOB_KEY, title, context -> {
            try {
                return run(profile, request, context);
            } finally {
                if (!request.dryRun()) {
                    profileUpload.discard();
                }
            }
        });
    }

    EmberImportReport run(EmberProfile profile, EmberImportRequest request, JobContext context) {
        context.reportProgress(0, 0, READING_MESSAGE);
        EmberImportProgress progress = new EmberImportProgress(request.dryRun());
        EmberLibrary library = libraryReader.read(profile.databaseFile());
        Optional<EmberTvLibrary> tvLibrary = request.importTvShows()
                ? Optional.of(tvLibraryReader.read(profile.databaseFile()))
                : Optional.empty();
        Optional<EmberSettings> settings = readSettings(profile, request, progress);
        List<EmberKodiHost> kodiHosts = readKodiHosts(profile, request, progress);
        transactionTemplate.executeWithoutResult(status -> {
            importer.importLibrary(library, tvLibrary, settings, request, progress, context);
            kodiHostImporter.importHosts(kodiHosts, EmberPathTranslator.of(request.pathMappings()), progress);
            if (request.dryRun()) {
                status.setRollbackOnly();
            }
        });
        EmberImportReport report = progress.toReport();
        log.info("Ember import{} finished: {} movies created, {} updated, {} skipped; {} episodes created, {} updated",
                request.dryRun() ? " (dry run)" : "", report.moviesCreated(), report.moviesUpdated(),
                report.moviesSkipped(), report.tvShows().episodesCreated(), report.tvShows().episodesUpdated());
        return report;
    }

    private void requireNoRunningImport() {
        if (jobService.findAll().stream()
                .anyMatch(job -> JOB_KEY.equals(job.key()) && job.status() == JobStatus.RUNNING)) {
            throw new EmberProfileUploadException(LocalizedText.of("migration.upload.importRunning"));
        }
    }

    /** Faulty Kodi settings must not prevent the analysis of the rest of the profile. */
    private List<EmberKodiHost> inspectKodiHosts(EmberProfile profile) {
        try {
            return kodiHostsReader.read(profile);
        } catch (EmberProfileException e) {
            log.warn("Ember Kodi settings could not be read", e);
            return List.of();
        }
    }

    private List<EmberKodiHost> readKodiHosts(EmberProfile profile, EmberImportRequest request,
            EmberImportProgress progress) {
        if (!request.importKodiHosts()) {
            return List.of();
        }
        try {
            return kodiHostsReader.read(profile);
        } catch (EmberProfileException e) {
            progress.warn(LocalizedText.of("migration.warning.kodiHostsUnreadable").with("reason", e.localizedText()));
            return List.of();
        }
    }

    private Optional<EmberSettings> readSettings(EmberProfile profile, EmberImportRequest request,
            EmberImportProgress progress) {
        if (!request.importSettings() || profile.settingsFile().isEmpty()) {
            return Optional.empty();
        }
        EmberSettings settings;
        try {
            settings = settingsReader.read(profile.settingsFile().get());
        } catch (EmberSettingsException e) {
            progress.warn(LocalizedText.of("migration.warning.settingsUnreadable").with("reason", e.localizedText()));
            return Optional.empty();
        }
        try {
            return Optional.of(settings.withRename(advancedSettingsReader.readRenameSettings(profile).orElse(null)));
        } catch (EmberSettingsException e) {
            progress.warn(LocalizedText.of("migration.warning.renameSettingsUnreadable")
                    .with("reason", e.localizedText()));
            return Optional.of(settings);
        }
    }
}
