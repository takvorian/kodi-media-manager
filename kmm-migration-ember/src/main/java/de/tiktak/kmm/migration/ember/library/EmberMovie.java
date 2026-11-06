package de.tiktak.kmm.migration.ember.library;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * A movie from the Ember database with all linked data. Empty texts are {@code null}, missing lists are empty.
 *
 * @param path       path of the movie file as Ember stored it (Windows)
 * @param mpaa       age rating, e.g. {@code Germany:16}
 * @param uniqueIds  IDs per provider, keys in lower case (e.g. {@code imdb}, {@code tmdb}, {@code tmdbcol})
 * @param sets       membership in movie sets
 * @param streams    video, audio and subtitle streams
 * @param userRating user rating, or {@code null} if none is given
 * @param language   language of the movie, e.g. {@code de-DE}, or {@code null}
 * @param trailer    trailer link, e.g. {@code plugin://plugin.video.youtube/…}, or {@code null}
 */
@Builder
public record EmberMovie(
        long id,
        long sourceId,
        String path,
        String title,
        String originalTitle,
        String sortTitle,
        Integer year,
        LocalDate premiered,
        String plot,
        String outline,
        String tagline,
        Integer runtime,
        String mpaa,
        Map<String, String> uniqueIds,
        List<String> genres,
        List<EmberActor> actors,
        List<String> directors,
        List<String> writers,
        List<String> studios,
        List<String> countries,
        List<String> tags,
        List<EmberRating> ratings,
        List<EmberSetLink> sets,
        EmberStreams streams,
        LocalDateTime dateAdded,
        Integer playCount,
        LocalDateTime lastPlayed,
        Integer userRating,
        String edition,
        String videoSource,
        String trailer,
        String language) {

    public EmberMovie {
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
        genres = copy(genres);
        actors = copy(actors);
        directors = copy(directors);
        writers = copy(writers);
        studios = copy(studios);
        countries = copy(countries);
        tags = copy(tags);
        ratings = copy(ratings);
        sets = copy(sets);
        streams = streams == null ? EmberStreams.NONE : streams;
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * An actor in the order from Ember.
     *
     * @param thumb URL of the image or {@code null}
     */
    public record EmberActor(String name, String role, String thumb) {
    }

    /**
     * A rating.
     *
     * @param source e.g. {@code imdb}, {@code themoviedb}, {@code trakt}
     */
    public record EmberRating(String source, double value, Integer max, Integer votes, boolean defaultRating) {
    }

    /**
     * Membership in a movie set.
     *
     * @param order position in the set, or {@code null} if Ember knows none
     */
    public record EmberSetLink(long setId, Integer order) {
    }
}
