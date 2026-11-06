package de.tiktak.kmm.settings;

/**
 * Order in which scrapers count the seasons and episodes of a TV show.
 */
public enum EpisodeOrdering {
    /** Order of first airing (default at TVDB and TMDB). */
    AIRED,
    /** Order of the DVD/Blu-ray release. */
    DVD,
    /** Continuous numbering without seasons, e.g. for anime. */
    ABSOLUTE
}
