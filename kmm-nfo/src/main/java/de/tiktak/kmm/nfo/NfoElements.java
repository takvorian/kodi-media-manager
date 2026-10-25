package de.tiktak.kmm.nfo;

/**
 * Element- und Attributnamen des Kodi-NFO-Formats für Filme (Kodi 19–21).
 */
final class NfoElements {

    static final String MOVIE = "movie";
    static final String TITLE = "title";
    static final String ORIGINAL_TITLE = "originaltitle";
    static final String SORT_TITLE = "sorttitle";
    static final String YEAR = "year";
    static final String PREMIERED = "premiered";
    static final String PLOT = "plot";
    static final String OUTLINE = "outline";
    static final String TAGLINE = "tagline";
    static final String RUNTIME = "runtime";
    static final String MPAA = "mpaa";
    static final String GENRE = "genre";
    static final String UNIQUE_ID = "uniqueid";
    static final String TYPE_ATTRIBUTE = "type";
    static final String DEFAULT_ATTRIBUTE = "default";

    /** Ältere Schreibweisen, die nur gelesen werden. */
    static final String LEGACY_ID = "id";
    static final String LEGACY_IMDB_ID = "imdbid";
    static final String LEGACY_TMDB_ID = "tmdbid";

    private NfoElements() {
    }
}
