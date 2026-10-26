package de.tiktak.kmm.tvshow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param preferredTvShowScraper Serien-Scraper für das automatische Scrapen; ist er nicht verfügbar,
 *                               wird der erste eingerichtete genutzt
 */
@ConfigurationProperties("kmm.scraping")
public record TvScrapingProperties(@DefaultValue("tvdb") String preferredTvShowScraper) {
}
