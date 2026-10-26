package de.tiktak.kmm.nfo;

/**
 * Element- und Attributnamen des Kodi-NFO-Formats für Filme, Serien und Episoden (Kodi 19–21).
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
    static final String ACTOR = "actor";
    static final String NAME = "name";
    static final String ROLE = "role";
    static final String ORDER = "order";
    static final String THUMB = "thumb";
    static final String DIRECTOR = "director";
    static final String CREDITS = "credits";
    static final String STUDIO = "studio";
    static final String COUNTRY = "country";
    static final String TAG = "tag";
    static final String RATINGS = "ratings";
    static final String RATING = "rating";
    static final String VALUE = "value";
    static final String VOTES = "votes";
    static final String MAX_ATTRIBUTE = "max";
    static final String NAME_ATTRIBUTE = "name";
    static final String SET = "set";
    static final String OVERVIEW = "overview";
    static final String USER_RATING = "userrating";
    static final String PLAY_COUNT = "playcount";
    static final String LAST_PLAYED = "lastplayed";
    static final String DATE_ADDED = "dateadded";

    static final String TV_SHOW = "tvshow";
    static final String EPISODE_DETAILS = "episodedetails";
    static final String STATUS = "status";
    static final String NAMED_SEASON = "namedseason";
    static final String NUMBER_ATTRIBUTE = "number";
    static final String SEASON = "season";
    static final String EPISODE = "episode";
    static final String AIRED = "aired";

    /** Ältere Schreibweisen, die nur gelesen werden. */
    static final String LEGACY_ID = "id";
    static final String LEGACY_IMDB_ID = "imdbid";
    static final String LEGACY_TMDB_ID = "tmdbid";
    /** Ember und ältere Scraper schreiben in die {@code tvshow.nfo} {@code <id>} (TVDB), {@code <imdb>}, {@code <tmdb>}. */
    static final String LEGACY_IMDB = "imdb";
    static final String LEGACY_TMDB = "tmdb";

    private NfoElements() {
    }
}
