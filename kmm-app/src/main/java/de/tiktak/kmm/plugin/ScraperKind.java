package de.tiktak.kmm.plugin;

import java.util.Arrays;
import java.util.List;

import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.MovieSetScraper;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import de.tiktak.kmm.plugin.api.TvShowScraper;

/**
 * What a scraper delivers. The order of the scrapers can be chosen per kind.
 */
public enum ScraperKind {
    /** Titles, plots and the rest of the metadata of movies and TV shows; the scraper an entry is identified with. */
    METADATA(MetadataScraper.class, TvShowScraper.class),
    ARTWORK(ImageScraper.class),
    /** Ratings and details for empty fields. */
    ENRICHMENT(EnrichmentScraper.class),
    THEME(ThemeScraper.class),
    TRAILER(TrailerScraper.class),
    MOVIE_SET(MovieSetScraper.class);

    private final List<Class<? extends Scraper>> types;

    @SafeVarargs
    ScraperKind(Class<? extends Scraper>... types) {
        this.types = List.of(types);
    }

    public boolean isProvidedBy(Scraper scraper) {
        return types.stream().anyMatch(type -> type.isInstance(scraper));
    }

    static List<ScraperKind> of(Scraper scraper) {
        return Arrays.stream(values()).filter(kind -> kind.isProvidedBy(scraper)).toList();
    }
}
