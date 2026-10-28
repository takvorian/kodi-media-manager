package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * Ein Trailer zur Auswahl.
 *
 * @param name       Titel des Videos
 * @param kodiUrl    Link, den Kodi abspielt und der in die NFO kommt, z. B.
 *                   {@code plugin://plugin.video.youtube/?action=play_video&videoid=…}
 * @param webUrl     Adresse zum Ansehen im Browser
 * @param previewUrl Vorschaubild oder {@code null}
 * @param language   Sprache (ISO 639-1) oder {@code null}
 * @param type       Art laut Anbieter, z. B. {@code Trailer} oder {@code Teaser}
 * @param official   ob der Anbieter das Video als offiziell kennzeichnet
 * @param height     Bildhöhe, z. B. 1080, oder {@code null}
 * @param published  Veröffentlichung als ISO-Datum oder {@code null}
 */
public record TrailerCandidate(
        String name,
        String kodiUrl,
        URI webUrl,
        URI previewUrl,
        String language,
        String type,
        boolean official,
        Integer height,
        String published) {

    public TrailerCandidate {
        Objects.requireNonNull(kodiUrl, "kodiUrl must not be null");
    }
}
