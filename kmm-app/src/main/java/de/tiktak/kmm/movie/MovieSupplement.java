package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Further details of a movie besides the editable metadata: contributors, assignments,
 * ratings and user data. Texts are trimmed and shortened to the column lengths, empty
 * entries are dropped.
 *
 * @param writers    screenwriters
 * @param set        movie set or {@code null}
 * @param userRating own rating (1-10) or {@code null}
 * @param playCount  number of plays or {@code null}
 * @param lastPlayed last play or {@code null}
 */
public record MovieSupplement(
        List<Actor> actors,
        List<String> directors,
        List<String> writers,
        List<String> studios,
        List<String> countries,
        List<String> tags,
        List<Rating> ratings,
        SetReference set,
        Integer userRating,
        Integer playCount,
        Instant lastPlayed) {

    static final int MAX_NAME_LENGTH = 512;
    static final int MAX_THUMB_LENGTH = 1024;
    static final int MAX_LABEL_LENGTH = 255;
    static final int MAX_RATING_SOURCE_LENGTH = 64;

    public static final MovieSupplement EMPTY = new MovieSupplement(List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), null, null, null, null);

    public MovieSupplement {
        actors = withoutNulls(actors);
        directors = normalizeTexts(directors, MAX_NAME_LENGTH);
        writers = normalizeTexts(writers, MAX_NAME_LENGTH);
        studios = normalizeTexts(studios, MAX_LABEL_LENGTH);
        countries = normalizeTexts(countries, MAX_LABEL_LENGTH);
        tags = normalizeTexts(tags, MAX_LABEL_LENGTH);
        ratings = withoutNulls(ratings);
    }

    /** Copy with different actors. */
    public MovieSupplement withActors(List<Actor> newActors) {
        return new MovieSupplement(newActors, directors, writers, studios, countries, tags, ratings, set, userRating,
                playCount, lastPlayed);
    }

    /** Copy with different ratings. */
    public MovieSupplement withRatings(List<Rating> newRatings) {
        return new MovieSupplement(actors, directors, writers, studios, countries, tags, newRatings, set, userRating,
                playCount, lastPlayed);
    }

    /** Copy with different tags. */
    public MovieSupplement withTags(List<String> newTags) {
        return new MovieSupplement(actors, directors, writers, studios, countries, newTags, ratings, set, userRating,
                playCount, lastPlayed);
    }

    /**
     * An actor; the order results from the position in the list.
     *
     * @param role  role or {@code null}
     * @param thumb URL or path of the image or {@code null}
     */
    public record Actor(String name, String role, String thumb) {

        public Actor {
            name = Objects.requireNonNull(Texts.clean(name, MAX_NAME_LENGTH), "name must not be blank");
            role = Texts.clean(role, MAX_NAME_LENGTH);
            thumb = Texts.clean(thumb, MAX_THUMB_LENGTH);
        }
    }

    /**
     * A rating.
     *
     * @param source        source, e.g. {@code imdb}, {@code themoviedb}
     * @param max           maximum value of the scale or {@code null}
     * @param votes         number of votes or {@code null}
     * @param defaultRating whether this rating is shown in Kodi
     */
    public record Rating(String source, double value, Integer max, Integer votes, boolean defaultRating) {

        public Rating {
            source = Objects.requireNonNull(Texts.clean(source, MAX_RATING_SOURCE_LENGTH),
                    "source must not be blank");
        }
    }

    /**
     * Reference to a movie set.
     *
     * @param overview         description of the set or {@code null}
     * @param tmdbCollectionId TMDB ID of the collection or {@code null}
     */
    public record SetReference(String name, String overview, String tmdbCollectionId) {

        private static final int MAX_ID_LENGTH = 16;

        public SetReference {
            name = Objects.requireNonNull(Texts.clean(name, MAX_NAME_LENGTH), "name must not be blank");
            overview = Texts.clean(overview, Integer.MAX_VALUE);
            tmdbCollectionId = Texts.clean(tmdbCollectionId, MAX_ID_LENGTH);
        }

        /** A set without a TMDB ID. */
        public SetReference(String name, String overview) {
            this(name, overview, null);
        }
    }

    private static <T> List<T> withoutNulls(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private static List<String> normalizeTexts(List<String> values, int maxLength) {
        return values == null ? List.of() : values.stream()
                .map(value -> Texts.clean(value, maxLength))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
