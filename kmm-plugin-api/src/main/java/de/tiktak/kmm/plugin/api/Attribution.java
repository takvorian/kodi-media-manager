package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Attribution of the data source that a provider requires in its terms of use. KMM shows it wherever data of this
 * scraper is used.
 *
 * @param text attribution text, e.g. "Metadata provided by TheTVDB."
 * @param link page of the provider the attribution links to
 */
public record Attribution(LocalizedText text, URI link) {

    public Attribution {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(link, "link must not be null");
    }
}
