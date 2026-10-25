package de.tiktak.kmm.migration.ember.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.migration.ember.EmberFixtures;
import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileException;

class EmberKodiHostsReaderTest {

    private final EmberKodiHostsReader reader = new EmberKodiHostsReader();

    @TempDir
    Path directory;

    @Test
    void readsHostsWithCredentialsAndSources() {
        EmberFixtures.copyKodiSettings(directory);

        List<EmberKodiHost> hosts = reader.read(profile());

        assertThat(hosts).containsExactly(
                new EmberKodiHost("Wohnzimmer", "kodi.example.org", 8080, "kodi", "test-passwort", true, List.of(
                        new EmberKodiHost.Source("Movie", "D:\\Filme", "smb://nas/medien/filme/"),
                        new EmberKodiHost.Source("TV", "D:\\Serien", "smb://nas/medien/serien/"))),
                new EmberKodiHost(null, "192.168.1.20", 0, null, null, false, List.of()));
    }

    @Test
    void returnsNoHostsWithoutKodiSettings() {
        assertThat(reader.read(profile())).isEmpty();
    }

    @Test
    void readsAFileWithoutHosts() throws IOException {
        Files.writeString(directory.resolve(EmberKodiHostsReader.FILE_NAME),
                "<interface.kodi><sendnotifications>false</sendnotifications></interface.kodi>");

        assertThat(reader.read(profile())).isEmpty();
    }

    @Test
    void reportsUnreadableFiles() throws IOException {
        Files.writeString(directory.resolve(EmberKodiHostsReader.FILE_NAME), "<interface.kodi><host>");

        assertThatThrownBy(() -> reader.read(profile()))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("Interface.Kodi.xml");
    }

    private EmberProfile profile() {
        return new EmberProfile(directory, directory.resolve(EmberFixtures.DATABASE_FILE_NAME), 48, Optional.empty());
    }
}
