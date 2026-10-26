package de.tiktak.kmm.tvshow;

/**
 * Eine Bewertung aus einer Quelle (TVDB, TMDB, IMDb …).
 *
 * @param max          Höchstwert der Skala oder {@code null}
 * @param votes        Anzahl der Stimmen oder {@code null}
 * @param defaultRating ob Kodi diese Bewertung anzeigt
 */
public record TvRating(String source, double value, Integer max, Integer votes, boolean defaultRating) {

    static final int MAX_SOURCE_LENGTH = 64;

    public TvRating {
        source = TvTexts.clean(source, MAX_SOURCE_LENGTH);
    }
}
