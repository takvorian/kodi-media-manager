package de.tiktak.kmm.migration.ember;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;

class EmberProfileInspectorTest {

    private final EmberProfileInspector inspector = new EmberProfileInspector(
            new EmberProfileDetector(), new EmberDatabaseInspector(), new EmberSettingsReader());

    @TempDir
    Path directory;

    @Test
    void inspectsCompleteProfile() {
        EmberFixtures.createProfile(directory);

        EmberProfileInspection inspection = inspector.inspect(directory);

        assertThat(inspection.profile().schemaVersion()).isEqualTo(48);
        assertThat(inspection.library().movies()).isEqualTo(EmberFixtures.MOVIE_COUNT);
        assertThat(inspection.settings()).hasValueSatisfying(
                settings -> assertThat(settings.language()).isEqualTo("de-DE"));
    }

    @Test
    void inspectsProfileWithoutSettings() {
        EmberFixtures.createDatabase(directory, EmberFixtures.DATABASE_FILE_NAME);

        EmberProfileInspection inspection = inspector.inspect(directory);

        assertThat(inspection.settings()).isEmpty();
        assertThat(inspection.library().episodes()).isEqualTo(EmberFixtures.EPISODE_COUNT);
    }
}
