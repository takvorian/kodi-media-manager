package de.tiktak.kmm.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scrapers that come first as long as no order has been chosen in the settings.
 *
 * @param preferredMetadataScraper movie scraper for automatic scraping
 * @param preferredTvShowScraper   TV show scraper for automatic scraping
 */
@ConfigurationProperties("kmm.scraping")
public record ScraperDefaults(@DefaultValue("tmdb") String preferredMetadataScraper,
        @DefaultValue("tvdb") String preferredTvShowScraper) {
}
