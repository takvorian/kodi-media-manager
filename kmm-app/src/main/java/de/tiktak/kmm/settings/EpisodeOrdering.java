package de.tiktak.kmm.settings;

/**
 * Reihenfolge, in der Scraper Staffeln und Episoden einer Serie zählen.
 */
public enum EpisodeOrdering {
    /** Reihenfolge der Erstausstrahlung (Standard bei TVDB und TMDB). */
    AIRED,
    /** Reihenfolge der DVD-/Blu-ray-Veröffentlichung. */
    DVD,
    /** Fortlaufende Nummerierung ohne Staffeln, z. B. bei Anime. */
    ABSOLUTE
}
