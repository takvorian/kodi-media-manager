package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import de.tiktak.kmm.plugin.api.PluginContext;

class PluginContextFactoryTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final PluginProperties properties = new PluginProperties(Path.of("plugins"), true,
            Map.of("tmdb", Map.of("api-key", "geheim", "language", "de-DE")));

    @Test
    @SuppressWarnings("unchecked")
    void providesSettingsOfThePluginHttpClientAndVersionedUserAgent() {
        ObjectProvider<BuildProperties> buildProperties = mock(ObjectProvider.class);
        Properties build = new Properties();
        build.setProperty("version", "1.2.3");
        when(buildProperties.getIfAvailable()).thenReturn(new BuildProperties(build));

        PluginContext context = new PluginContextFactory(properties, httpClient, buildProperties,
                () -> Map.of("language", "en-US", "region", "US"), (id, settings) -> settings).contextFor("tmdb");

        assertThat(context.setting("api-key")).contains("geheim");
        assertThat(context.setting("language")).as("own setting takes precedence").contains("de-DE");
        assertThat(context.setting("region")).contains("US");
        assertThat(context.setting("unbekannt")).isEmpty();
        assertThat(context.httpClient()).isSameAs(httpClient);
        assertThat(context.userAgent()).startsWith("KodiMediaManager/1.2.3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givesPluginsWithoutSettingsAnEmptyConfiguration() {
        ObjectProvider<BuildProperties> buildProperties = mock(ObjectProvider.class);

        PluginContext context = new PluginContextFactory(properties, httpClient, buildProperties, Map::of,
                (id, settings) -> settings).contextFor("andere");

        assertThat(context.setting("api-key")).isEmpty();
        assertThat(context.userAgent()).startsWith("KodiMediaManager/dev");
    }

    @Test
    @SuppressWarnings("unchecked")
    void letsStoredKeysOverrideTheConfiguration() {
        ObjectProvider<BuildProperties> buildProperties = mock(ObjectProvider.class);

        PluginContext context = new PluginContextFactory(properties, httpClient, buildProperties, Map::of,
                (id, settings) -> {
                    Map<String, String> overlaid = new HashMap<>(settings);
                    overlaid.put("api-key", "gespeichert-" + id);
                    return overlaid;
                }).contextFor("tmdb");

        assertThat(context.setting("api-key")).contains("gespeichert-tmdb");
        assertThat(context.setting("language")).contains("de-DE");
    }

    @Test
    void treatsMissingSettingsAsEmpty() {
        assertThat(new PluginProperties(Path.of("plugins"), true, null).settingsOf("tmdb")).isEmpty();
    }
}
