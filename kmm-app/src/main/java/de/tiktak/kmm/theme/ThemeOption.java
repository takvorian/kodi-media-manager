package de.tiktak.kmm.theme;

import java.net.URI;

import de.tiktak.kmm.plugin.api.ThemeCandidate;

/**
 * A theme music for selection in the movie or TV show view.
 *
 * @param subject     TV show or movie at the provider
 * @param source      address the music is loaded from
 * @param previewUrl  directly playable audio file or {@code null}
 * @param webUrl      page of the provider
 * @param sameTitle   whether the provider lists it under the same title; only such matches are applied automatically
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
