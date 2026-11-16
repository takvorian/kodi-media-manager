package de.tiktak.kmm.plugin;

import static org.mockito.Mockito.mock;

/** Scraper orders for tests of the services that use them, without a database. */
public final class ScraperPriorityTesting {

    private ScraperPriorityTesting() {
    }

    /** No order chosen anywhere: the preferred scrapers first, the others by name; nothing switched off. */
    public static ScraperPriorityService defaults(ScraperRegistry registry, String preferredMovieScraper,
            String preferredTvShowScraper) {
        return new ScraperPriorityService(registry, mock(ScraperPriorityStore.class),
                new ScraperDefaults(preferredMovieScraper, preferredTvShowScraper));
    }

    public static ScraperPriorityService defaults(ScraperRegistry registry) {
        return defaults(registry, "tmdb", "tvdb");
    }
}
