package de.tiktak.kmm.scanner.naming;

import java.util.HashSet;
import java.util.Set;

/**
 * Die Platzhalter der Namensmuster je Medientyp.
 */
public final class NamePlaceholders {

    public static final String TITLE = "title";
    public static final String ORIGINAL_TITLE = "originaltitle";
    public static final String SORT_TITLE = "sorttitle";
    public static final String YEAR = "year";
    public static final String EDITION = "edition";
    public static final String SET = "set";
    public static final String IMDB = "imdb";
    public static final String TMDB = "tmdb";
    public static final String GENRES = "genres";
    public static final String DIRECTORS = "directors";
    public static final String COUNTRIES = "countries";
    public static final String MPAA = "mpaa";
    public static final String RATING = "rating";
    public static final String RESOLUTION = "resolution";
    public static final String VIDEO_CODEC = "videocodec";
    public static final String AUDIO_CODEC = "audiocodec";
    public static final String CHANNELS = "channels";
    public static final String SOURCE = "source";
    public static final String THREE_D = "3d";
    public static final String SHOW = "show";
    public static final String TVDB = "tvdb";
    public static final String SEASON = "season";
    public static final String EPISODE = "episode";
    public static final String AIRED = "aired";

    /** Platzhalter für Filmordner und -dateien. */
    public static final Set<String> MOVIE = Set.of(TITLE, ORIGINAL_TITLE, SORT_TITLE, YEAR, EDITION, SET, IMDB, TMDB,
            GENRES, DIRECTORS, COUNTRIES, MPAA, RATING, RESOLUTION, VIDEO_CODEC, AUDIO_CODEC, CHANNELS, SOURCE,
            THREE_D);

    /** Platzhalter für Serienordner. */
    public static final Set<String> TV_SHOW = Set.of(SHOW, ORIGINAL_TITLE, SORT_TITLE, YEAR, IMDB, TMDB, TVDB, GENRES,
            MPAA, RATING);

    /** Platzhalter für Staffelordner. */
    public static final Set<String> SEASON_FOLDER = union(TV_SHOW, Set.of(SEASON));

    /** Platzhalter für Episodendateien; {@code {title}} ist der Titel der Episode. */
    public static final Set<String> EPISODE_FILE = union(SEASON_FOLDER, Set.of(EPISODE, TITLE, AIRED, RESOLUTION,
            VIDEO_CODEC, AUDIO_CODEC, CHANNELS, SOURCE, THREE_D));

    private NamePlaceholders() {
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> all = new HashSet<>(first);
        all.addAll(second);
        return Set.copyOf(all);
    }
}
