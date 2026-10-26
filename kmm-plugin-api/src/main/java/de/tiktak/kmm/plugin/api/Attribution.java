package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * Hinweis auf die Datenquelle, den ein Anbieter in seinen Nutzungsbedingungen verlangt. KMM zeigt ihn
 * überall an, wo Daten dieses Scrapers genutzt werden.
 *
 * @param text Hinweistext, z. B. „Metadata provided by TheTVDB.“
 * @param link Seite des Anbieters, auf die der Hinweis verlinkt
 */
public record Attribution(String text, URI link) {

    public Attribution {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(link, "link must not be null");
    }
}
