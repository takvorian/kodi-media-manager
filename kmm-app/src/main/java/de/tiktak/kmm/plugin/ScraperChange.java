package de.tiktak.kmm.plugin;

import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code PUT .../scraper} of a movie or TV show.
 *
 * @param scraperId the chosen metadata scraper, or {@code null} for the order of the source
 * @param rescrape  whether the entry is identified again with it and its texts are fetched again right away
 */
public record ScraperChange(@Pattern(regexp = "[a-z0-9][a-z0-9_.-]{0,39}") String scraperId, boolean rescrape) {
}
