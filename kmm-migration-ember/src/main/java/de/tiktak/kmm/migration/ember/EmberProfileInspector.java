package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;
import java.util.Optional;

import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Analysiert ein Ember-Profil, ohne etwas zu verändern: Profil erkennen, Bibliotheksumfang
 * ermitteln und Einstellungen einlesen.
 */
@Slf4j
@RequiredArgsConstructor
public class EmberProfileInspector {

    private final EmberProfileDetector profileDetector;
    private final EmberDatabaseInspector databaseInspector;
    private final EmberSettingsReader settingsReader;

    public EmberProfileInspection inspect(Path directory) {
        EmberProfile profile = profileDetector.detect(directory);
        log.info("Ember profile found in {} (schema version {})", profile.directory(), profile.schemaVersion());
        EmberLibraryStatistics library = databaseInspector.inspect(profile.databaseFile());
        Optional<EmberSettings> settings = profile.settingsFile().map(settingsReader::read);
        return new EmberProfileInspection(profile, library, settings);
    }
}
