package de.tiktak.kmm.plugin.api;

import java.util.List;

/**
 * Supplies artwork candidates for a medium. Images are looked up via known IDs ({@link ScrapeRequest#uniqueIds()})
 * because image services offer no reliable title search.
 */
public interface ImageScraper extends Scraper {

    /**
     * @return found images of all types the scraper knows; empty if the medium is unknown
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    List<ImageCandidate> findImages(ScrapeRequest request);
}
