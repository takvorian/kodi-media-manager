package de.tiktak.kmm.movie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param preferredMetadataScraper Scraper für das automatische Scrapen; ist er nicht verfügbar, wird
 *                                 der erste eingerichtete Metadaten-Scraper genutzt
 */
@ConfigurationProperties("kmm.scraping")
public record ScrapingProperties(@DefaultValue("tmdb") String preferredMetadataScraper) {
}
