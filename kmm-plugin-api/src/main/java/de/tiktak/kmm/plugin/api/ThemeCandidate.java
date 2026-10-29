package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * Eine Titelmusik zur Auswahl.
 *
 * @param name            Bezeichnung beim Anbieter, z. B. {@code Main Theme} oder der Titel eines Videos
 * @param subject         Serie bzw. Film, zu dem der Anbieter die Musik führt, z. B. {@code ALF}; daran erkennt KMM
 *                        sichere Treffer
 * @param source          Adresse, aus der der Scraper die Musik lädt (Audiodatei oder Video)
 * @param previewUrl      direkt abspielbare Audiodatei zum Anhören oder {@code null}
 * @param webUrl          Seite des Anbieters zum Ansehen im Browser
 * @param durationSeconds Länge in Sekunden oder {@code null}
 */
public record ThemeCandidate(
        String name,
        String subject,
        URI source,
        URI previewUrl,
        URI webUrl,
        Integer durationSeconds) {

    public ThemeCandidate {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(webUrl, "webUrl must not be null");
    }
}
