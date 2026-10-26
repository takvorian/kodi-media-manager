package de.tiktak.kmm.plugin;

import java.util.Set;

import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;

/**
 * Darstellung eines verfügbaren Scrapers für die REST-API.
 *
 * @param missingConfiguration Hinweis, was zur Nutzung fehlt, oder {@code null}, wenn einsatzbereit
 * @param attribution          vom Anbieter verlangter Hinweis auf die Datenquelle oder {@code null}
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
