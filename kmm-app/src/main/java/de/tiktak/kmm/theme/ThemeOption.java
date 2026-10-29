package de.tiktak.kmm.theme;

import java.net.URI;

import de.tiktak.kmm.plugin.api.ThemeCandidate;

/**
 * Eine Titelmusik zur Auswahl in der Film- oder Serienansicht.
 *
 * @param subject     Serie bzw. Film beim Anbieter
 * @param source      Adresse, aus der die Musik geladen wird
 * @param previewUrl  direkt abspielbare Audiodatei oder {@code null}
 * @param webUrl      Seite des Anbieters
 * @param sameTitle   ob der Anbieter sie unter demselben Titel führt; nur solche Treffer übernimmt KMM automatisch
 */
public record ThemeOption(String name, String subject, URI source, URI previewUrl, URI webUrl,
        Integer durationSeconds, String scraperId, String scraperName, boolean sameTitle) {

    static ThemeOption of(ThemeCandidate candidate, String scraperId, String scraperName, boolean sameTitle) {
        return new ThemeOption(candidate.name(), candidate.subject(), candidate.source(), candidate.previewUrl(),
                candidate.webUrl(), candidate.durationSeconds(), scraperId, scraperName, sameTitle);
    }

    ThemeCandidate toCandidate() {
        return new ThemeCandidate(name, subject, source, previewUrl, webUrl, durationSeconds);
    }
}
