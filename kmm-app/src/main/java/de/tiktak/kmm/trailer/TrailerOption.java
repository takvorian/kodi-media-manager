package de.tiktak.kmm.trailer;

import java.net.URI;

import de.tiktak.kmm.plugin.api.TrailerCandidate;

/**
 * A trailer for selection in the movie or TV show view.
 *
 * @param url         link for the NFO that Kodi plays
 * @param webUrl      address for viewing in the browser
 * @param previewUrl  preview image or {@code null}
 * @param language    language (ISO 639-1) or {@code null}
 * @param scraperName provider, e.g. "The Movie Database (TMDB)"
 */
public record TrailerOption(String name, String url, URI webUrl, URI previewUrl, String language, String type,
        boolean official, Integer height, String published, String scraperName) {

    static TrailerOption of(TrailerCandidate candidate, String scraperName) {
        return new TrailerOption(candidate.name(), candidate.kodiUrl(), candidate.webUrl(), candidate.previewUrl(),
                candidate.language(), candidate.type(), candidate.official(), candidate.height(),
                candidate.published(), scraperName);
    }
}
