package de.tiktak.kmm.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Liefert Angaben zu Filmsets (Sammlungen wie „Matrix Filmreihe“).
 */
public interface MovieSetScraper extends Scraper {

    /**
     * Sucht passende Sammlungen; der Nutzer wählt daraus einen Treffer aus.
     *
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    List<SearchResult> searchSets(String title);

    /**
     * Lädt Titel und Beschreibung einer Sammlung in der eingestellten Sprache.
     *
     * @return leer, wenn der Anbieter die ID nicht kennt
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    Optional<MovieSetResult> fetchSet(String providerId);
}
