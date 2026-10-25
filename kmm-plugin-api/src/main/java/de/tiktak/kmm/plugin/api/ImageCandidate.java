package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

import lombok.Builder;

/**
 * Ein von einem Image-Scraper gefundenes Bild.
 *
 * @param url        Bild in voller Auflösung; wird beim Übernehmen heruntergeladen
 * @param previewUrl kleine Vorschau für die Auswahl; ohne eigene Vorschau gleich {@code url}
 * @param language   ISO-639-1-Sprachcode oder {@code null} für sprachneutrale Bilder
 * @param rating     Beliebtheit beim Anbieter (größer ist besser) oder {@code null}
 */
@Builder
public record ImageCandidate(
        ImageType type,
        URI url,
        URI previewUrl,
        Integer width,
        Integer height,
        String language,
        Double rating) {

    public ImageCandidate {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(url, "url must not be null");
        previewUrl = previewUrl == null ? url : previewUrl;
    }
}
