package de.tiktak.kmm.plugin.api;

import java.util.List;

/**
 * Liefert Artwork-Kandidaten zu einem Medium.
 */
public interface ImageScraper extends Scraper {

    List<ImageCandidate> findImages(ScrapeRequest request);
}
