package de.tiktak.kmm.plugin.api;

import java.util.List;

/**
 * Liefert Trailer zu einem Film oder einer Serie als Links, die Kodi abspielen kann. Gesucht wird über bekannte IDs
 * ({@link ScrapeRequest#uniqueIds()}).
 */
public interface TrailerScraper extends Scraper {

    /**
     * @return gefundene Trailer in der Reihenfolge des Anbieters; leer, wenn der Eintrag unbekannt ist
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    List<TrailerCandidate> findTrailers(ScrapeRequest request);
}
