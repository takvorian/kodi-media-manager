package de.tiktak.kmm.plugin;

import java.util.List;

/**
 * The metadata scraper of a movie or TV show for choosing another one.
 *
 * @param scraperId        the scraper chosen for the entry, or {@code null} if the order of its source applies
 * @param defaultScraperId the scraper that applies without an own choice, or {@code null} if none is set up
 * @param scrapers         the scrapers to choose from, in the order of the source
 */
public record ScraperChoice(String scraperId, String defaultScraperId, List<ScraperInfo> scrapers) {

    public ScraperChoice {
        scrapers = List.copyOf(scrapers);
    }
}
