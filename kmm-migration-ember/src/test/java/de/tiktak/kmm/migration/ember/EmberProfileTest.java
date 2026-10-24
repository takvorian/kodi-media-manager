package de.tiktak.kmm.migration.ember;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class EmberProfileTest {

    private static final Path DIRECTORY = Path.of("profile");
    private static final Path DATABASE = DIRECTORY.resolve("MyVideos48.emm");

    @Test
    void rejectsMissingDirectory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EmberProfile(null, DATABASE, 48, Optional.empty()))
                .withMessageContaining("directory");
    }

    @Test
    void rejectsMissingDatabaseFile() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EmberProfile(DIRECTORY, null, 48, Optional.empty()))
                .withMessageContaining("databaseFile");
    }

    @Test
    void rejectsNullInsteadOfEmptySettings() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EmberProfile(DIRECTORY, DATABASE, 48, null))
                .withMessageContaining("settingsFile");
    }
}
