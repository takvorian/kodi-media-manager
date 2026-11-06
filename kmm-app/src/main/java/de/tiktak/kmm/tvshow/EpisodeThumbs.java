package de.tiktak.kmm.tvshow;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Stills of an episode at the TV show scrapers.
 *
 * @param warnings notes about scrapers that are not set up, do not know the TV show or report an error
 */
public record EpisodeThumbs(List<Thumb> thumbs, List<LocalizedText> warnings) {

    public EpisodeThumbs {
        thumbs = List.copyOf(thumbs);
        warnings = List.copyOf(warnings);
    }

    /**
     * A still of a provider.
     */
    public record Thumb(String scraperId, String scraperName, URI url) {

        public Thumb {
            Objects.requireNonNull(url, "url must not be null");
        }
    }
}
