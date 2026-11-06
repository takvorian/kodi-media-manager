package de.tiktak.kmm.movie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param preferredMetadataScraper scraper for automatic scraping; if it is unavailable,
 *                                 the first metadata scraper that is set up is used
 */
@ConfigurationProperties("kmm.scraping")
public record ScrapingProperties(@DefaultValue("tmdb") String preferredMetadataScraper) {
}
