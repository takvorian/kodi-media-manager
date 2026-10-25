package de.tiktak.kmm.plugin.api;

/**
 * Der Anbieter eines Scrapers ist nicht erreichbar oder lehnt die Anfrage ab. Die Meldung wird dem
 * Nutzer angezeigt und sollte die Ursache verständlich benennen.
 */
public class ScraperException extends RuntimeException {

    public ScraperException(String message) {
        super(message);
    }

    public ScraperException(String message, Throwable cause) {
        super(message, cause);
    }
}
