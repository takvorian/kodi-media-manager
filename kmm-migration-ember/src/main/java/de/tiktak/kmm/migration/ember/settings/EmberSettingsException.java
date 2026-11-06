package de.tiktak.kmm.migration.ember.settings;

import java.nio.file.Path;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.migration.ember.EmberProfileException;

/**
 * The {@code Settings.xml} of an Ember profile cannot be read.
 */
public class EmberSettingsException extends EmberProfileException {

    EmberSettingsException(Path settingsFile, Throwable cause) {
        super(LocalizedText.of("ember.settingsUnreadable").with("file", settingsFile), cause);
    }
}
