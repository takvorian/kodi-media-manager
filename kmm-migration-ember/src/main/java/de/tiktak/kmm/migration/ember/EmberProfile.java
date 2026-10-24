package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein gefundenes Ember-Profil (Inhalt von {@code <Ember>\Profiles\Default}).
 *
 * @param directory     Profilverzeichnis
 * @param databaseFile  SQLite-Datenbank {@code MyVideos<version>.emm}
 * @param schemaVersion Schema-Version laut Dateiname
 * @param settingsFile  {@code Settings.xml}, sofern vorhanden
 */
public record EmberProfile(Path directory, Path databaseFile, int schemaVersion, Optional<Path> settingsFile) {

    public EmberProfile {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(databaseFile, "databaseFile must not be null");
        Objects.requireNonNull(settingsFile, "settingsFile must not be null");
    }
}
