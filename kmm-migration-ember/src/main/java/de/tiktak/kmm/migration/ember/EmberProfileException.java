package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;

/**
 * An Ember profile is missing, incomplete or cannot be read. The message is worded for end users.
 */
public class EmberProfileException extends LocalizedException {

    protected EmberProfileException(LocalizedText message) {
        super(message);
    }

    protected EmberProfileException(LocalizedText message, Throwable cause) {
        super(message, cause);
    }

    static EmberProfileException directoryNotFound(Path directory) {
        return new EmberProfileException(LocalizedText.of("ember.directoryNotFound").with("directory", directory));
    }

    static EmberProfileException databaseNotFound(Path directory) {
        return new EmberProfileException(LocalizedText.of("ember.databaseNotFound").with("directory", directory));
    }

    static EmberProfileException unsupportedSchemaVersion(int foundVersion, int supportedVersion) {
        return new EmberProfileException(LocalizedText.of("ember.unsupportedSchemaVersion")
                .with("found", foundVersion)
                .with("supported", supportedVersion));
    }

    static EmberProfileException missingTables(Path databaseFile, Iterable<String> tables) {
        return new EmberProfileException(LocalizedText.of("ember.missingTables")
                .with("file", databaseFile)
                .with("tables", String.join(", ", tables)));
    }

    public static EmberProfileException unreadableDatabase(Path databaseFile, Throwable cause) {
        return new EmberProfileException(LocalizedText.of("ember.databaseUnreadable").with("file", databaseFile),
                cause);
    }

    public static EmberProfileException unreadableKodiSettings(Path file, Throwable cause) {
        return new EmberProfileException(LocalizedText.of("ember.kodiSettingsUnreadable").with("file", file), cause);
    }
}
