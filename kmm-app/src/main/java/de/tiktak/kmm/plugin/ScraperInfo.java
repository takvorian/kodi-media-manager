package de.tiktak.kmm.plugin;

import java.util.Set;

import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;

/**
 * Representation of an available scraper for the REST API.
 *
 * @param missingConfiguration hint about what is missing for use, or {@code null} if ready
 * @param attribution          notice about the data source required by the provider, or {@code null}
 */
public record ScraperInfo(String id, String displayName, Set<MediaType> mediaTypes, String missingConfiguration,
        Attribution attribution) {

    public ScraperInfo {
        mediaTypes = Set.copyOf(mediaTypes);
    }

    static ScraperInfo from(Scraper scraper) {
        return new ScraperInfo(scraper.id(), scraper.displayName(), scraper.supportedMediaTypes(),
                scraper.missingConfiguration().orElse(null), scraper.attribution().orElse(null));
    }
}
