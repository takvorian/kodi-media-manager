package de.tiktak.kmm.trailer;

import java.net.URI;

import de.tiktak.kmm.plugin.api.TrailerCandidate;

/**
 * Ein Trailer zur Auswahl in der Film- oder Serienansicht.
 *
 * @param url         Link für die NFO, den Kodi abspielt
 * @param webUrl      Adresse zum Ansehen im Browser
 * @param previewUrl  Vorschaubild oder {@code null}
 * @param language    Sprache (ISO 639-1) oder {@code null}
 * @param scraperName Anbieter, z. B. „The Movie Database (TMDB)“
 */
public record TrailerOption(String name, String url, URI webUrl, URI previewUrl, String language, String type,
        boolean official, Integer height, String published, String scraperName) {

    static TrailerOption of(TrailerCandidate candidate, String scraperName) {
        return new TrailerOption(candidate.name(), candidate.kodiUrl(), candidate.webUrl(), candidate.previewUrl(),
                candidate.language(), candidate.type(), candidate.official(), candidate.height(),
                candidate.published(), scraperName);
    }
}
