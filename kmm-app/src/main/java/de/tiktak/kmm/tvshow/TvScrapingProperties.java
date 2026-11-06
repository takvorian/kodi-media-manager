package de.tiktak.kmm.tvshow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param preferredTvShowScraper TV show scraper for automatic scraping; if it is not available,
 *                               the first one that is set up is used
 */
@ConfigurationProperties("kmm.scraping")
public record TvScrapingProperties(@DefaultValue("tvdb") String preferredTvShowScraper) {
}
