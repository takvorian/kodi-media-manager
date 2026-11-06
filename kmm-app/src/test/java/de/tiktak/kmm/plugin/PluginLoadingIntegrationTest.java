package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

/**
 * Loads the real test plugin ({@code kmm-plugin-testdata}) via PF4J and checks the path from the JAR to
 * the scraper call. Maven copies the plugin JAR to {@code target/test-plugins} beforehand.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class PluginLoadingIntegrationTest {

    private static final String TEST_PLUGINS_DIRECTORY_PROPERTY = "kmm.test-plugins.directory";
    private static final String TEST_PLUGIN_JAR = "kmm-plugin-testdata.jar";
    private static final String TEST_PLUGIN_ID = "testdata";

    @TempDir
    static Path pluginDirectory;

    @Autowired
    private ScraperRegistry scraperRegistry;

    @Autowired
    private PluginService pluginService;

    @DynamicPropertySource
    static void pluginDirectory(DynamicPropertyRegistry registry) throws IOException {
        Path builtPlugin = Path.of(System.getProperty(TEST_PLUGINS_DIRECTORY_PROPERTY), TEST_PLUGIN_JAR);
        Files.copy(builtPlugin, pluginDirectory.resolve(TEST_PLUGIN_JAR));
        registry.add("kmm.plugins.directory", pluginDirectory::toString);
    }

    @Test
    void loadsPluginAndProvidesItsScraper() {
        assertThat(pluginService.installedPlugins())
                .singleElement()
                .satisfies(plugin -> {
                    assertThat(plugin.id()).isEqualTo(TEST_PLUGIN_ID);
                    assertThat(plugin.state()).isEqualTo("STARTED");
                });

        var result = scraperRegistry.metadataScrapersFor(MediaType.MOVIE).getFirst()
                .scrape(new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, null));

        assertThat(result).hasValueSatisfying(metadata -> assertThat(metadata.title()).isEqualTo("Heat"));
    }

    @Test
    void removesScrapersOfDisabledPluginAndRestoresThemOnEnable() {
        pluginService.disable(TEST_PLUGIN_ID);
        assertThat(scraperRegistry.metadataScrapers()).isEmpty();

        pluginService.enable(TEST_PLUGIN_ID);
        assertThat(scraperRegistry.metadataScrapers()).hasSize(1);
    }
}
