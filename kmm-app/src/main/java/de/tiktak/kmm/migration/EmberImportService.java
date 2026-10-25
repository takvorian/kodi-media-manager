package de.tiktak.kmm.migration;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileDetector;
import de.tiktak.kmm.migration.ember.EmberProfileInspector;
import de.tiktak.kmm.migration.ember.library.EmberLibrary;
import de.tiktak.kmm.migration.ember.library.EmberLibraryReader;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsException;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Analysiert Ember-Profile und übernimmt ihren Filmbestand als Hintergrund-Job. Ein Probelauf
 * führt den Import vollständig aus und rollt ihn am Ende zurück; so zeigt sein Bericht genau, was
 * der echte Import ändern würde. Ein erneuter Import aktualisiert statt zu duplizieren.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmberImportService {

    public static final String JOB_KEY = "ember-import";
    static final String READING_MESSAGE = "Ember-Datenbank wird gelesen";

    private final EmberProfileInspector profileInspector;
    private final EmberProfileDetector profileDetector;
    private final EmberLibraryReader libraryReader;
    private final EmberSettingsReader settingsReader;
    private final EmberLibraryImporter importer;
    private final JobService jobService;
    private final TransactionTemplate transactionTemplate;

    public EmberInspectionResponse inspect(Path directory) {
        var inspection = profileInspector.inspect(directory);
        List<EmberMovieSource> sources = libraryReader.readSources(inspection.profile().databaseFile());
        return EmberInspectionResponse.from(inspection, sources);
    }

    /**
     * Prüft das Profil und startet den Import.
     *
     * @throws de.tiktak.kmm.migration.ember.EmberProfileException wenn das Profil ungültig ist
     */
    public JobSnapshot start(EmberImportRequest request) {
        EmberProfile profile = profileDetector.detect(Path.of(request.profilePath()));
        String title = request.dryRun() ? "Ember-Import (Probelauf)" : "Ember-Import";
        return jobService.submit(JOB_KEY, title, context -> run(profile, request, context));
    }

    EmberImportReport run(EmberProfile profile, EmberImportRequest request, JobContext context) {
        context.reportProgress(0, 0, READING_MESSAGE);
        EmberImportProgress progress = new EmberImportProgress(request.dryRun());
        EmberLibrary library = libraryReader.read(profile.databaseFile());
        Optional<EmberSettings> settings = readSettings(profile, request, progress);
        transactionTemplate.executeWithoutResult(status -> {
            importer.importLibrary(library, settings, request, progress, context);
            if (request.dryRun()) {
                status.setRollbackOnly();
            }
        });
        EmberImportReport report = progress.toReport();
        log.info("Ember import{} finished: {} movies created, {} updated, {} skipped",
                request.dryRun() ? " (dry run)" : "", report.moviesCreated(), report.moviesUpdated(),
                report.moviesSkipped());
        return report;
    }

    private Optional<EmberSettings> readSettings(EmberProfile profile, EmberImportRequest request,
            EmberImportProgress progress) {
        if (!request.importSettings() || profile.settingsFile().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(settingsReader.read(profile.settingsFile().get()));
        } catch (EmberSettingsException e) {
            progress.warn("Die Ember-Einstellungen konnten nicht gelesen werden: " + e.getMessage());
            return Optional.empty();
        }
    }
}
