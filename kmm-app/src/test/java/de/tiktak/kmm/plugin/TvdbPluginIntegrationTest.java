package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Loads the real TVDB plugin JAR via PF4J and checks that it appears as a TV show scraper with the
 * attribution required by TVDB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class TvdbPluginIntegrationTest {

    private static final String TVDB_PLUGIN_JAR = "kmm-plugin-tvdb.jar";

    @TempDir
    static Path pluginDirectory;

    @Autowired
    private MockMvcTester mockMvc;

    @DynamicPropertySource
    static void tvdbPlugin(DynamicPropertyRegistry registry) throws IOException {
        Path builtPlugin = Path.of(System.getProperty("kmm.test-plugins.directory"), TVDB_PLUGIN_JAR);
        Files.copy(builtPlugin, pluginDirectory.resolve(TVDB_PLUGIN_JAR));
        registry.add("kmm.plugins.directory", pluginDirectory::toString);
        registry.add("kmm.plugins.settings.tvdb.api-key", () -> "tvdb-test-key");
    }

    @Test
    void listsTheTvdbPluginAsTvShowScraperWithAttribution() {
        assertThat(mockMvc.get().uri(ScraperController.TV_SHOW_SCRAPERS_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "tvdb", "displayName": "TheTVDB", "mediaTypes": ["TV_SHOW"],
                          "missingConfiguration": null,
                          "attribution": {"link": "https://www.thetvdb.com"}}]
                        """);
    }
}
