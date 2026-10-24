package de.tiktak.kmm.plugin.api;

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
}
