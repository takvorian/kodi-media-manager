package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.SettingDefinition;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;
import de.tiktak.kmm.settings.PluginSettingView;

@WebMvcTest(controllers = ScraperController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class ScraperControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private ScraperRegistry scraperRegistry;

    @MockitoBean
    private ScraperOverviewService overviewService;

    @Test
    void listsMetadataScrapersSortedByNameWithConfigurationHint() {
        LocalizedText missingKey = LocalizedText.of("scraper.apiKeyMissing").with("provider", "TMDB");
        MetadataScraper tmdb = scraper("tmdb", "The Movie Database", Optional.of(missingKey));
        MetadataScraper imdb = scraper("imdb", "IMDb", Optional.empty());
        when(scraperRegistry.metadataScrapers()).thenReturn(List.of(tmdb, imdb));

        assertThat(mockMvc.get().uri(ScraperController.METADATA_SCRAPERS_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "imdb", "displayName": "IMDb", "mediaTypes": ["MOVIE"], "missingConfiguration": null},
                         {"id": "tmdb",
                          "missingConfiguration": {"key": "scraper.apiKeyMissing", "args": {"provider": "TMDB"}}}]
                        """);
    }

    @Test
    void listsTvShowScrapersWithAttribution() {
        TvShowScraper tvdb = mock(TvShowScraper.class);
        when(tvdb.id()).thenReturn("tvdb");
        when(tvdb.displayName()).thenReturn("TheTVDB");
        when(tvdb.supportedMediaTypes()).thenReturn(Set.of(MediaType.TV_SHOW));
        when(tvdb.attribution()).thenReturn(Optional.of(
                new Attribution(LocalizedText.of("scraper.tvdb.attribution"), URI.create("https://thetvdb.com"))));
        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(tvdb));

        assertThat(mockMvc.get().uri(ScraperController.TV_SHOW_SCRAPERS_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "tvdb", "mediaTypes": ["TV_SHOW"], "missingConfiguration": null,
                          "attribution": {"text": {"key": "scraper.tvdb.attribution", "args": {}},
                                          "link": "https://thetvdb.com"}}]
                        """);
    }

    @Test
    void showsAScraperPluginWithItsSettings() {
        ScraperOverview tvdb = new ScraperOverview("tvdb", "TheTVDB", "1.0", "TVDB", true,
                List.of(new ScraperOverview.Capability(ScraperKind.METADATA, Set.of(MediaType.TV_SHOW))), null, null);
        when(overviewService.details("tvdb")).thenReturn(new ScraperDetails(tvdb, List.of(new PluginSettingView(
                "api-key", SettingDefinition.Type.SECRET, LocalizedText.of("scraper.setting.tvdb.apiKey"), null,
                null, true, "…abcd"))));

        assertThat(mockMvc.get().uri(ScraperController.PLUGIN_PATH, "tvdb"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"scraper": {"id": "tvdb", "enabled": true,
                                     "capabilities": [{"kind": "METADATA", "mediaTypes": ["TV_SHOW"]}]},
                         "settings": [{"name": "api-key", "type": "SECRET", "set": true, "hint": "…abcd",
                                       "value": null}]}
                        """);
    }

    @Test
    void changesTheSettingsOfAPlugin() {
        ScraperOverview omdb = new ScraperOverview("omdb", "OMDb", "1.0", null, true, List.of(), null, null);
        when(overviewService.changeSettings("omdb", Map.of("api-key", ""))).thenReturn(
                new ScraperDetails(omdb, List.of()));

        assertThat(mockMvc.put().uri(ScraperController.PLUGIN_PATH + "/settings", "omdb").with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"api-key\": \"\"}"))
                .hasStatusOk();
        verify(overviewService).changeSettings("omdb", Map.of("api-key", ""));
    }

    @Test
    void switchesAPluginOff() {
        ScraperOverview trakt = new ScraperOverview("trakt", "trakt", "1.0", null, false, List.of(), null, null);
        when(overviewService.disable("trakt")).thenReturn(new ScraperDetails(trakt, List.of()));

        assertThat(mockMvc.post().uri(ScraperController.PLUGIN_PATH + "/disable", "trakt").with(csrf()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"scraper": {"id": "trakt", "enabled": false}, "settings": []}
                        """);
    }

    private static MetadataScraper scraper(String id, String name, Optional<LocalizedText> missing) {
        MetadataScraper scraper = mock(MetadataScraper.class);
        when(scraper.id()).thenReturn(id);
        when(scraper.displayName()).thenReturn(name);
        when(scraper.supportedMediaTypes()).thenReturn(Set.of(MediaType.MOVIE));
        when(scraper.missingConfiguration()).thenReturn(missing);
        return scraper;
    }
}
