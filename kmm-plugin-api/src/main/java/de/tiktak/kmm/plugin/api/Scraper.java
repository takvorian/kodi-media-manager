package de.tiktak.kmm.plugin.api;

import java.util.Optional;
import java.util.Set;

import org.pf4j.ExtensionPoint;

/**
 * Gemeinsame Eigenschaften aller Scraper-Erweiterungen.
 */
public interface Scraper extends ExtensionPoint {

    /**
     * Eindeutige, stabile Kennung des Scrapers, z. B. {@code tmdb}.
     */
    String id();

    String displayName();

    Set<MediaType> supportedMediaTypes();

    default boolean supports(MediaType mediaType) {
        return supportedMediaTypes().contains(mediaType);
    }

    /**
     * Wird von KMM einmal nach dem Erzeugen aufgerufen, bevor der Scraper benutzt wird.
     */
    default void initialize(PluginContext context) {
    }

    /**
     * @return Hinweis für den Nutzer, was zur Nutzung fehlt (z. B. ein API-Key), oder leer, wenn
     *         der Scraper einsatzbereit ist
     */
    default Optional<String> missingConfiguration() {
        return Optional.empty();
    }

    /**
     * @return Hinweis auf die Datenquelle, den der Anbieter verlangt, oder leer
     */
    default Optional<Attribution> attribution() {
        return Optional.empty();
    }
}
