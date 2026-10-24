package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * Ein von einem Image-Scraper gefundenes Bild.
 *
 * @param language ISO-639-1-Sprachcode oder {@code null} für sprachneutrale Bilder
 */
public record ImageCandidate(ImageType type, URI url, Integer width, Integer height, String language) {

    public ImageCandidate {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(url, "url must not be null");
    }
}
