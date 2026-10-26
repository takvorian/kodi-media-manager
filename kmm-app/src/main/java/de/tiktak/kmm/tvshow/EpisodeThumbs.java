package de.tiktak.kmm.tvshow;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Standbilder einer Episode bei den Serien-Scrapern.
 *
 * @param warnings Hinweise zu Scrapern, die nicht eingerichtet sind, die Serie nicht kennen oder einen Fehler melden
 */
public record EpisodeThumbs(List<Thumb> thumbs, List<String> warnings) {

    public EpisodeThumbs {
        thumbs = List.copyOf(thumbs);
        warnings = List.copyOf(warnings);
    }

    /**
     * Ein Standbild eines Anbieters.
     */
    public record Thumb(String scraperId, String scraperName, URI url) {

        public Thumb {
            Objects.requireNonNull(url, "url must not be null");
        }
    }
}
