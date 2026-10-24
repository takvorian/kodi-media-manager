package de.tiktak.kmm.plugin.api;

import java.util.Optional;

/**
 * Liefert Metadaten (Titel, Handlung, IDs …) zu einem Medium.
 */
public interface MetadataScraper extends Scraper {

    Optional<MetadataResult> scrape(ScrapeRequest request);
}
