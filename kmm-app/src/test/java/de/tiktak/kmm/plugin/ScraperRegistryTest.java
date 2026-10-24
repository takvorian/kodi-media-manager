package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginManager;

import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataScraper;

@ExtendWith(MockitoExtension.class)
class ScraperRegistryTest {

    @Mock
    private PluginManager pluginManager;

    @InjectMocks
    private ScraperRegistry registry;

    @Test
    void returnsMetadataScrapersOfStartedPlugins() {
        MetadataScraper scraper = mock(MetadataScraper.class);
        when(pluginManager.getExtensions(MetadataScraper.class)).thenReturn(List.of(scraper));

        assertThat(registry.metadataScrapers()).containsExactly(scraper);
    }

    @Test
    void returnsImageScrapersOfStartedPlugins() {
        ImageScraper scraper = mock(ImageScraper.class);
        when(pluginManager.getExtensions(ImageScraper.class)).thenReturn(List.of(scraper));

        assertThat(registry.imageScrapers()).containsExactly(scraper);
    }

    @Test
    void filtersMetadataScrapersByMediaType() {
        MetadataScraper movieScraper = metadataScraperFor(MediaType.MOVIE);
        MetadataScraper tvScraper = metadataScraperFor(MediaType.TV_SHOW);
        when(pluginManager.getExtensions(MetadataScraper.class)).thenReturn(List.of(movieScraper, tvScraper));

        assertThat(registry.metadataScrapersFor(MediaType.TV_SHOW)).containsExactly(tvScraper);
    }

    @Test
    void filtersImageScrapersByMediaType() {
        ImageScraper movieScraper = imageScraperFor(MediaType.MOVIE);
        ImageScraper tvScraper = imageScraperFor(MediaType.TV_SHOW);
        when(pluginManager.getExtensions(ImageScraper.class)).thenReturn(List.of(movieScraper, tvScraper));

        assertThat(registry.imageScrapersFor(MediaType.MOVIE)).containsExactly(movieScraper);
    }

    @Test
    void returnsNothingWithoutPlugins() {
        when(pluginManager.getExtensions(MetadataScraper.class)).thenReturn(List.of());

        assertThat(registry.metadataScrapersFor(MediaType.MOVIE)).isEmpty();
    }

    private static MetadataScraper metadataScraperFor(MediaType mediaType) {
        MetadataScraper scraper = mock(MetadataScraper.class);
        when(scraper.supports(any())).thenCallRealMethod();
        when(scraper.supportedMediaTypes()).thenReturn(Set.of(mediaType));
        return scraper;
    }

    private static ImageScraper imageScraperFor(MediaType mediaType) {
        ImageScraper scraper = mock(ImageScraper.class);
        when(scraper.supports(any())).thenCallRealMethod();
        when(scraper.supportedMediaTypes()).thenReturn(Set.of(mediaType));
        return scraper;
    }
}
