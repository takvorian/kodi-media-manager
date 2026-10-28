package de.tiktak.kmm.movie;

import de.tiktak.kmm.plugin.api.MetadataResult;

/**
 * Legt Scraper-Ergebnisse über vorhandene Metadaten: Was der Scraper liefert, ersetzt den bisherigen
 * Wert; was er nicht kennt (z. B. Sortiertitel oder Kurzinhalt), bleibt erhalten.
 */
final class ScrapedMetadataMerger {

    static final String IMDB = "imdb";
    static final String TMDB = "tmdb";

    private ScrapedMetadataMerger() {
    }

    static MovieMetadata merge(MovieMetadata current, MetadataResult scraped) {
        return new MovieMetadata(
                scraped.title(),
                prefer(scraped.originalTitle(), current.originalTitle()),
                current.sortTitle(),
                prefer(scraped.year(), current.year()),
                prefer(scraped.premiered(), current.premiered()),
                prefer(scraped.plot(), current.plot()),
                current.outline(),
                prefer(scraped.tagline(), current.tagline()),
                prefer(scraped.runtime(), current.runtime()),
                prefer(scraped.certification(), current.mpaa()),
                scraped.genres().isEmpty() ? current.genres() : scraped.genres(),
                prefer(scraped.uniqueIds().get(IMDB), current.imdbId()),
                prefer(scraped.uniqueIds().get(TMDB), current.tmdbId()),
                current.edition(), current.videoSource());
    }

    private static <T> T prefer(T scraped, T current) {
        return scraped == null ? current : scraped;
    }
}
