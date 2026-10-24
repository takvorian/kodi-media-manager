package de.tiktak.kmm.migration.ember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmberProfileDetectorTest {

    private final EmberProfileDetector detector = new EmberProfileDetector();

    @TempDir
    Path directory;

    @Test
    void detectsProfileWithDatabaseAndSettings() throws IOException {
        Files.createFile(directory.resolve("MyVideos48.emm"));
        Files.createFile(directory.resolve("Settings.xml"));

        EmberProfile profile = detector.detect(directory);

        assertThat(profile.directory()).isEqualTo(directory);
        assertThat(profile.databaseFile()).isEqualTo(directory.resolve("MyVideos48.emm"));
        assertThat(profile.schemaVersion()).isEqualTo(EmberProfileDetector.SUPPORTED_SCHEMA_VERSION);
        assertThat(profile.settingsFile()).contains(directory.resolve("Settings.xml"));
    }

    @Test
    void detectsProfileWithoutSettings() throws IOException {
        Files.createFile(directory.resolve("MyVideos48.emm"));

        EmberProfile profile = detector.detect(directory);

        assertThat(profile.settingsFile()).isEmpty();
    }

    @Test
    void resolvesDefaultProfileInsideInstallationDirectory() throws IOException {
        Path profileDirectory = Files.createDirectories(directory.resolve("Profiles").resolve("Default"));
        Files.createFile(profileDirectory.resolve("MyVideos48.emm"));

        EmberProfile profile = detector.detect(directory);

        assertThat(profile.directory()).isEqualTo(profileDirectory);
    }

    @Test
    void matchesDatabaseFileNameCaseInsensitively() throws IOException {
        Files.createFile(directory.resolve("myvideos48.EMM"));

        EmberProfile profile = detector.detect(directory);

        assertThat(profile.schemaVersion()).isEqualTo(48);
    }

    @Test
    void picksNewestDatabaseWhenOlderVersionsRemain() throws IOException {
        Files.createFile(directory.resolve("MyVideos47.emm"));
        Files.createFile(directory.resolve("MyVideos48.emm"));

        EmberProfile profile = detector.detect(directory);

        assertThat(profile.databaseFile().getFileName()).hasToString("MyVideos48.emm");
    }

    @Test
    void ignoresDirectoriesAndUnrelatedFiles() throws IOException {
        Files.createDirectory(directory.resolve("MyVideos49.emm"));
        Files.createFile(directory.resolve("MyVideos48.emm.bak"));
        Files.createFile(directory.resolve("MyVideos48.emm"));

        EmberProfile profile = detector.detect(directory);

        assertThat(profile.schemaVersion()).isEqualTo(48);
    }

    @Test
    void rejectsMissingDirectory() {
        Path missing = directory.resolve("missing");

        assertThatThrownBy(() -> detector.detect(missing))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("existiert nicht");
    }

    @Test
    void rejectsDirectoryWithoutDatabase() {
        assertThatThrownBy(() -> detector.detect(directory))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("keine Ember-Datenbank");
    }

    @Test
    void rejectsOutdatedSchemaVersionWithHint() throws IOException {
        Files.createFile(directory.resolve("MyVideos47.emm"));

        assertThatThrownBy(() -> detector.detect(directory))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("Schema-Version 47")
                .hasMessageContaining("letzten Ember-Version");
    }

    @Test
    void rejectsNewerSchemaVersion() throws IOException {
        Files.createFile(directory.resolve("MyVideos49.emm"));

        assertThatThrownBy(() -> detector.detect(directory))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("Schema-Version 49");
    }
}
