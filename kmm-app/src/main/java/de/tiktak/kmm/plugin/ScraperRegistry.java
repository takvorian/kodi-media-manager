package de.tiktak.kmm.plugin;

import java.util.List;

import org.pf4j.PluginManager;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.Scraper;
import lombok.RequiredArgsConstructor;

/**
 * Zentrale Stelle, über die der Rest der Anwendung an die Scraper der gestarteten Plugins kommt.
 * Kapselt PF4J, damit nur dieses Paket die Plugin-Bibliothek kennt.
 */
@Component
@RequiredArgsConstructor
public class ScraperRegistry {

    private final PluginManager pluginManager;

    public List<MetadataScraper> metadataScrapers() {
        return pluginManager.getExtensions(MetadataScraper.class);
    }

    public List<ImageScraper> imageScrapers() {
        return pluginManager.getExtensions(ImageScraper.class);
    }

    public List<MetadataScraper> metadataScrapersFor(MediaType mediaType) {
        return supporting(metadataScrapers(), mediaType);
    }

    public List<ImageScraper> imageScrapersFor(MediaType mediaType) {
        return supporting(imageScrapers(), mediaType);
    }

    private static <T extends Scraper> List<T> supporting(List<T> scrapers, MediaType mediaType) {
        return scrapers.stream()
                .filter(scraper -> scraper.supports(mediaType))
                .toList();
    }
}
