package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Weitere Angaben zu einem Film neben den bearbeitbaren Metadaten: Mitwirkende, Zuordnungen,
 * Bewertungen und Nutzerdaten. Texte werden getrimmt und auf die Spaltenlängen gekürzt, leere
 * Einträge entfallen.
 *
 * @param writers    Drehbuchautoren
 * @param set        Filmset oder {@code null}
 * @param userRating eigene Bewertung (1–10) oder {@code null}
 * @param playCount  Anzahl der Wiedergaben oder {@code null}
 * @param lastPlayed letzte Wiedergabe oder {@code null}
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

    /** Kopie mit anderen Bewertungen. */
    public MovieSupplement withRatings(List<Rating> newRatings) {
        return new MovieSupplement(actors, directors, writers, studios, countries, tags, newRatings, set, userRating,
                playCount, lastPlayed);
    }

    /** Kopie mit anderen Tags. */
    public MovieSupplement withTags(List<String> newTags) {
        return new MovieSupplement(actors, directors, writers, studios, countries, newTags, ratings, set, userRating,
                playCount, lastPlayed);
    }

    /**
     * Ein Darsteller; die Reihenfolge ergibt sich aus der Position in der Liste.
     *
     * @param role  Rolle oder {@code null}
     * @param thumb URL oder Pfad des Bildes oder {@code null}
     */
    public record Actor(String name, String role, String thumb) {

        public Actor {
            name = Objects.requireNonNull(Texts.clean(name, MAX_NAME_LENGTH), "name must not be blank");
            role = Texts.clean(role, MAX_NAME_LENGTH);
            thumb = Texts.clean(thumb, MAX_THUMB_LENGTH);
        }
    }

    /**
     * Eine Bewertung.
     *
     * @param source        Quelle, z. B. {@code imdb}, {@code themoviedb}
     * @param max           Höchstwert der Skala oder {@code null}
     * @param votes         Anzahl der Stimmen oder {@code null}
     * @param defaultRating ob diese Bewertung in Kodi angezeigt wird
     */
    public record Rating(String source, double value, Integer max, Integer votes, boolean defaultRating) {

        public Rating {
            source = Objects.requireNonNull(Texts.clean(source, MAX_RATING_SOURCE_LENGTH),
                    "source must not be blank");
        }
    }

    /**
     * Verweis auf ein Filmset.
     *
     * @param overview         Beschreibung des Sets oder {@code null}
     * @param tmdbCollectionId TMDB-ID der Sammlung oder {@code null}
     */
    public record SetReference(String name, String overview, String tmdbCollectionId) {

        private static final int MAX_ID_LENGTH = 16;

        public SetReference {
            name = Objects.requireNonNull(Texts.clean(name, MAX_NAME_LENGTH), "name must not be blank");
            overview = Texts.clean(overview, Integer.MAX_VALUE);
            tmdbCollectionId = Texts.clean(tmdbCollectionId, MAX_ID_LENGTH);
        }

        /** Ein Set ohne TMDB-ID. */
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
