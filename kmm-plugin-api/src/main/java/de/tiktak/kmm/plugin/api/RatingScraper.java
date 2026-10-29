package de.tiktak.kmm.plugin.api;

import java.util.Optional;

/**
 * Liefert die Bewertung eines Anbieters zu einem Film oder einer Serie, etwa von Trakt. Gesucht wird über bekannte IDs
 * ({@link ScrapeRequest#uniqueIds()}).
 */
public interface RatingScraper extends Scraper {

    /** Kennung der Bewertung in der NFO, z. B. {@code trakt}; liegt sie schon vor, fragt KMM nicht erneut. */
    String ratingSource();

    /**
     * @return die Bewertung oder leer, wenn der Anbieter den Eintrag nicht kennt oder noch niemand bewertet hat
     * @throws ScraperException wenn der Anbieter nicht erreichbar ist oder die Anfrage ablehnt
     */
    Optional<ProviderRating> findRating(ScrapeRequest request);
}
