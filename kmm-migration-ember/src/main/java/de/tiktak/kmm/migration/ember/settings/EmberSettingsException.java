package de.tiktak.kmm.migration.ember.settings;

import java.nio.file.Path;

import de.tiktak.kmm.migration.ember.EmberProfileException;

/**
 * Die {@code Settings.xml} eines Ember-Profils kann nicht gelesen werden.
 */
public class EmberSettingsException extends EmberProfileException {

    EmberSettingsException(Path settingsFile, Throwable cause) {
        super("Die Ember-Einstellungen '%s' können nicht gelesen werden.".formatted(settingsFile), cause);
    }
}
