package de.tiktak.kmm.tvshow;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * Stills of an episode at the TV show scrapers.
 *
 * @param localizedWarnings notes about scrapers that are not set up, do not know the TV show or report an error
 */
public record EpisodeThumbs(List<Thumb> thumbs, List<LocalizedText> localizedWarnings) {

    public EpisodeThumbs {
        thumbs = List.copyOf(thumbs);
        localizedWarnings = List.copyOf(localizedWarnings);
    }

    /**
     * @return the notes in English
     * @deprecated use {@link #localizedWarnings()}, which the user interface can translate
     */
    @Deprecated(forRemoval = true)
    public List<String> warnings() {
        return localizedWarnings.stream().map(Messages::english).toList();
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
