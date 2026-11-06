package de.tiktak.kmm.migration.ember;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds an Ember profile in a directory and checks whether its database version is supported. Accepts both the
 * profile directory itself and the Ember installation directory.
 */
public class EmberProfileDetector {

    static final int SUPPORTED_SCHEMA_VERSION = 48;
    static final String SETTINGS_FILE_NAME = "Settings.xml";
    static final Path DEFAULT_PROFILE_SUBDIRECTORY = Path.of("Profiles", "Default");

    private static final Pattern DATABASE_FILE_NAME = Pattern.compile("MyVideos(\\d+)\\.emm", Pattern.CASE_INSENSITIVE);
    private static final int SCHEMA_VERSION_GROUP = 1;

    public EmberProfile detect(Path directory) {
        Path profileDirectory = resolveProfileDirectory(directory);
        DatabaseFile database = findNewestDatabase(profileDirectory)
                .orElseThrow(() -> EmberProfileException.databaseNotFound(profileDirectory));
        requireSupportedVersion(database);
        return new EmberProfile(profileDirectory, database.path(), database.schemaVersion(),
                findSettingsFile(profileDirectory));
    }

    private Path resolveProfileDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw EmberProfileException.directoryNotFound(directory);
        }
        Path defaultProfile = directory.resolve(DEFAULT_PROFILE_SUBDIRECTORY);
        return Files.isDirectory(defaultProfile) ? defaultProfile : directory;
    }

    private Optional<DatabaseFile> findNewestDatabase(Path profileDirectory) {
        try (Stream<Path> files = Files.list(profileDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .flatMap(file -> DatabaseFile.of(file).stream())
                    .max(Comparator.comparingInt(DatabaseFile::schemaVersion));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void requireSupportedVersion(DatabaseFile database) {
        if (database.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw EmberProfileException.unsupportedSchemaVersion(database.schemaVersion(), SUPPORTED_SCHEMA_VERSION);
        }
    }

    private Optional<Path> findSettingsFile(Path profileDirectory) {
        Path settingsFile = profileDirectory.resolve(SETTINGS_FILE_NAME);
        return Files.isRegularFile(settingsFile) ? Optional.of(settingsFile) : Optional.empty();
    }

    private record DatabaseFile(Path path, int schemaVersion) {

        static Optional<DatabaseFile> of(Path file) {
            Matcher matcher = DATABASE_FILE_NAME.matcher(file.getFileName().toString());
            if (!matcher.matches()) {
                return Optional.empty();
            }
            return Optional.of(new DatabaseFile(file, Integer.parseInt(matcher.group(SCHEMA_VERSION_GROUP))));
        }
    }
}
