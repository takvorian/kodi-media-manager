package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = ScraperController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class ScraperControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private ScraperRegistry scraperRegistry;

    @Test
    void listsMetadataScrapersSortedByNameWithConfigurationHint() {
        MetadataScraper tmdb = scraper("tmdb", "The Movie Database", Optional.of("API-Key fehlt"));
        MetadataScraper imdb = scraper("imdb", "IMDb", Optional.empty());
        when(scraperRegistry.metadataScrapers()).thenReturn(List.of(tmdb, imdb));

        assertThat(mockMvc.get().uri(ScraperController.METADATA_SCRAPERS_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "imdb", "displayName": "IMDb", "mediaTypes": ["MOVIE"], "missingConfiguration": null},
                         {"id": "tmdb", "missingConfiguration": "API-Key fehlt"}]
                        """);
    }

    private static MetadataScraper scraper(String id, String name, Optional<String> missing) {
        MetadataScraper scraper = mock(MetadataScraper.class);
        when(scraper.id()).thenReturn(id);
        when(scraper.displayName()).thenReturn(name);
        when(scraper.supportedMediaTypes()).thenReturn(Set.of(MediaType.MOVIE));
        when(scraper.missingConfiguration()).thenReturn(missing);
        return scraper;
    }
}
