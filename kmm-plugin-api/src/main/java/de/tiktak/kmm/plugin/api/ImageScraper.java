package de.tiktak.kmm.plugin.api;

import java.util.List;

/**
 * Liefert Artwork-Kandidaten zu einem Medium. Bilder werden über bekannte IDs gesucht
 * ({@link ScrapeRequest#uniqueIds()}), da Bilddienste keine verlässliche Titelsuche bieten.
 */
public interface ImageScraper extends Scraper {

    /**
     * @return gefundene Bilder aller Arten, die der Scraper kennt; leer, wenn das Medium unbekannt ist
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    List<ImageCandidate> findImages(ScrapeRequest request);
}
