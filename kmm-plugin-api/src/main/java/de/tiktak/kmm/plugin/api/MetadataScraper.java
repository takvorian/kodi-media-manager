package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Liefert Metadaten (Titel, Handlung, IDs …) zu einem Medium.
 */
public interface MetadataScraper extends Scraper {

    /**
     * Sucht passende Medien; der Nutzer wählt daraus einen Treffer aus.
     *
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    List<SearchResult> search(SearchQuery query);

    /**
     * Lädt alle Metadaten zu einem Treffer aus {@link #search(SearchQuery)}.
     *
     * @return leer, wenn der Anbieter die ID nicht kennt
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    Optional<MetadataResult> fetch(String providerId);

    /**
     * Ermittelt das Medium selbstständig, bevorzugt über bekannte IDs, sonst über den besten
     * Suchtreffer. Gedacht für die automatische Übernahme ohne Rückfrage.
     *
     * @return leer, wenn kein Treffer eindeutig genug ist
     */
    Optional<MetadataResult> scrape(ScrapeRequest request);
}
