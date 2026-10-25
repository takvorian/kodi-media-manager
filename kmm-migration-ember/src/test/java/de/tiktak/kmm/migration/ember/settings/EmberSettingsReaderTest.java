package de.tiktak.kmm.migration.ember.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.migration.ember.EmberFixtures;
import de.tiktak.kmm.migration.ember.EmberProfileException;

class EmberSettingsReaderTest {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    private final EmberSettingsReader reader = new EmberSettingsReader();

    @TempDir
    Path directory;

    @Test
    void readsSupportedSettings() {
        Path settingsFile = EmberFixtures.copySettings(directory);

        EmberSettings settings = reader.read(settingsFile);

        assertThat(settings.version()).isEqualTo(1);
        assertThat(settings.language()).isEqualTo("de-DE");
        assertThat(settings.videoExtensions()).containsExactly(".mkv", ".mp4", ".avi");
        assertThat(settings.subtitleExtensions()).containsExactly(".srt", ".sub");
        assertThat(settings.proxy()).contains(new EmberProxySettings("http://proxy.local", 3128, "proxyuser"));
        assertThat(settings.movieLanguage()).isEqualTo("de-DE");
        assertThat(settings.movieImageLanguage()).isEqualTo("de");
    }

    @Test
    void ignoresImageLanguageThatIsNotEnforced() throws IOException {
        Path settingsFile = writeSettings("""
                <Settings version="1">
                  <MovieImagesForceLanguage>false</MovieImagesForceLanguage>
                  <MovieImagesForcedLanguage>de</MovieImagesForcedLanguage>
                </Settings>
                """);

        assertThat(reader.read(settingsFile).movieImageLanguage()).isNull();
    }

    @Test
    void appliesDefaultsForMinimalSettings() throws IOException {
        Path settingsFile = writeSettings("<Settings version=\"1\" />");

        EmberSettings settings = reader.read(settingsFile);

        assertThat(settings.language()).isNull();
        assertThat(settings.videoExtensions()).isEmpty();
        assertThat(settings.subtitleExtensions()).isEmpty();
        assertThat(settings.proxy()).isEmpty();
    }

    @Test
    void treatsEmptyExtensionListAsEmpty() throws IOException {
        Path settingsFile = writeSettings("<Settings version=\"1\"><FileSystemValidExts /></Settings>");

        EmberSettings settings = reader.read(settingsFile);

        assertThat(settings.videoExtensions()).isEmpty();
    }

    @Test
    void readsProxyWithoutCredentials() throws IOException {
        Path settingsFile = writeSettings("""
                <Settings version="1">
                  <ProxyURI>http://proxy.local</ProxyURI>
                  <ProxyPort>8080</ProxyPort>
                  <ProxyCredentials><UserName /></ProxyCredentials>
                </Settings>
                """);

        EmberSettings settings = reader.read(settingsFile);

        assertThat(settings.proxy()).contains(new EmberProxySettings("http://proxy.local", 8080, null));
    }

    @Test
    void ignoresBlankProxyUri() throws IOException {
        Path settingsFile = writeSettings("<Settings version=\"1\"><ProxyURI>  </ProxyURI></Settings>");

        EmberSettings settings = reader.read(settingsFile);

        assertThat(settings.proxy()).isEmpty();
    }

    @Test
    void rejectsMalformedXml() throws IOException {
        Path settingsFile = writeSettings("<Settings version=\"1\">");

        assertThatThrownBy(() -> reader.read(settingsFile))
                .isInstanceOf(EmberSettingsException.class)
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("können nicht gelesen werden");
    }

    @Test
    void rejectsMissingFile() {
        Path settingsFile = directory.resolve("Settings.xml");

        assertThatThrownBy(() -> reader.read(settingsFile))
                .isInstanceOf(EmberSettingsException.class);
    }

    private Path writeSettings(String xml) throws IOException {
        return Files.writeString(directory.resolve("Settings.xml"), XML_HEADER + xml);
    }
}
