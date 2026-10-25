package de.tiktak.kmm.movie;

/**
 * Ergebnis von „fehlende Metadaten holen“ für einen Film.
 */
public enum MetadataScrapeOutcome {
    /** Metadaten wurden geholt und in die NFO geschrieben. */
    SCRAPED,
    /** Der Film hat bereits eine TMDB-ID und wurde nicht verändert. */
    ALREADY_IDENTIFIED,
    /** Kein Treffer war eindeutig genug; der Film muss von Hand zugeordnet werden. */
    NO_MATCH,
    /** Kein Metadaten-Scraper ist installiert und eingerichtet. */
    NO_SCRAPER
}
