package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;

/**
 * Ein Ember-Profil fehlt, ist unvollständig oder kann nicht gelesen werden. Die Meldung ist für
 * Endanwender formuliert.
 */
public class EmberProfileException extends RuntimeException {

    protected EmberProfileException(String message) {
        super(message);
    }

    protected EmberProfileException(String message, Throwable cause) {
        super(message, cause);
    }

    static EmberProfileException directoryNotFound(Path directory) {
        return new EmberProfileException("Das Verzeichnis '%s' existiert nicht.".formatted(directory));
    }

    static EmberProfileException databaseNotFound(Path directory) {
        return new EmberProfileException(
                "Im Verzeichnis '%s' wurde keine Ember-Datenbank (MyVideos*.emm) gefunden.".formatted(directory));
    }

    static EmberProfileException unsupportedSchemaVersion(int foundVersion, int supportedVersion) {
        return new EmberProfileException((
                "Die Ember-Datenbank hat die Schema-Version %d, unterstützt wird nur Version %d. "
                        + "Bitte das Profil einmal mit der letzten Ember-Version öffnen, damit Ember "
                        + "die Datenbank aktualisiert.").formatted(foundVersion, supportedVersion));
    }

    static EmberProfileException missingTables(Path databaseFile, Iterable<String> tables) {
        return new EmberProfileException("Die Datei '%s' ist keine gültige Ember-Datenbank, es fehlen die Tabellen: %s."
                .formatted(databaseFile, String.join(", ", tables)));
    }

    public static EmberProfileException unreadableDatabase(Path databaseFile, Throwable cause) {
        return new EmberProfileException(
                "Die Ember-Datenbank '%s' kann nicht gelesen werden.".formatted(databaseFile), cause);
    }

    public static EmberProfileException unreadableKodiSettings(Path file, Throwable cause) {
        return new EmberProfileException(
                "Die Ember-Einstellungen für Kodi '%s' können nicht gelesen werden.".formatted(file), cause);
    }
}
