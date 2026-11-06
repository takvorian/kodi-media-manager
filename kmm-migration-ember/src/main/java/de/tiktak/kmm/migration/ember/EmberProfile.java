package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A detected Ember profile (contents of {@code <Ember>\Profiles\Default}).
 *
 * @param directory     profile directory
 * @param databaseFile  SQLite database {@code MyVideos<version>.emm}
 * @param schemaVersion schema version according to the file name
 * @param settingsFile  {@code Settings.xml}, if present
 */
public record EmberProfile(Path directory, Path databaseFile, int schemaVersion, Optional<Path> settingsFile) {

    public EmberProfile {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(databaseFile, "databaseFile must not be null");
        Objects.requireNonNull(settingsFile, "settingsFile must not be null");
    }
}
