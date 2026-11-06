package de.tiktak.kmm.tvshow;

/**
 * A rating from a source (TVDB, TMDB, IMDb ...).
 *
 * @param max          maximum of the scale or {@code null}
 * @param votes        number of votes or {@code null}
 * @param defaultRating whether Kodi displays this rating
 */
public record TvRating(String source, double value, Integer max, Integer votes, boolean defaultRating) {

    static final int MAX_SOURCE_LENGTH = 64;

    public TvRating {
        source = TvTexts.clean(source, MAX_SOURCE_LENGTH);
    }
}
