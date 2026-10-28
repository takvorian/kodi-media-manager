package de.tiktak.kmm.migration.ember.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.migration.ember.EmberProfile;

class EmberAdvancedSettingsReaderTest {

    private final EmberAdvancedSettingsReader reader = new EmberAdvancedSettingsReader();

    @TempDir
    Path directory;

    @Test
    void readsTheSettingsOfTheRenamer() throws URISyntaxException {
        Path fixture = Path.of(getClass().getResource("/ember/advanced/AdvancedSettings.xml").toURI()).getParent();

        assertThat(reader.readRenameSettings(profile(fixture))).contains(new EmberRenameSettings("$T {($Y)}",
                "$T{.$S}", "$Z", "Staffel $K1_?", "$Z - $W2_S?2E?{ - $T}", false, true, true, null, false));
    }

    @Test
    void isEmptyWithoutTheFile() {
        assertThat(reader.readRenameSettings(profile(directory))).isEmpty();
    }

    @Test
    void reportsUnreadableFiles() throws IOException {
        Files.writeString(directory.resolve(EmberAdvancedSettingsReader.FILE_NAME), "kaputt <");

        assertThatThrownBy(() -> reader.readRenameSettings(profile(directory)))
                .isInstanceOf(EmberSettingsException.class);
    }

    private static EmberProfile profile(Path directory) {
        return new EmberProfile(directory, directory.resolve("MyVideos48.emm"), 48, Optional.empty());
    }
}
