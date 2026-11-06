package de.tiktak.kmm.plugin.api;

import java.util.List;

/**
 * Supplies trailers for a movie or TV show as links Kodi can play. The search uses known IDs
 * ({@link ScrapeRequest#uniqueIds()}).
 */
public interface TrailerScraper extends Scraper {

    /**
     * @return found trailers in the order of the provider; empty if the entry is unknown
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    List<TrailerCandidate> findTrailers(ScrapeRequest request);
}
