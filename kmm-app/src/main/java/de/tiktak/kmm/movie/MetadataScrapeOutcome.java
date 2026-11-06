package de.tiktak.kmm.movie;

/**
 * Result of "fetch missing metadata" for a movie.
 */
public enum MetadataScrapeOutcome {
    /** Metadata was fetched and written to the NFO. */
    SCRAPED,
    /** The movie already has a TMDB ID and was not changed. */
    ALREADY_IDENTIFIED,
    /** No match was unambiguous enough; the movie has to be assigned by hand. */
    NO_MATCH,
    /** No metadata scraper is installed and set up. */
    NO_SCRAPER
}
