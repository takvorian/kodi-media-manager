package de.tiktak.kmm.nfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Further details of a movie NFO besides the core metadata: cast and crew, assignments, ratings and user data.
 * Single values that are not set are {@code null}, missing lists are empty.
 *
 * @param writers    writers ({@code <credits>})
 * @param set        movie set or {@code null}
 * @param userRating own rating (0–10) or {@code null}
 * @param playCount  number of playbacks or {@code null}
 * @param lastPlayed last playback (local time) or {@code null}
 * @param dateAdded  date added to the library (local time) or {@code null}
 * @param streams    video, audio and subtitle streams; empty if unknown
 * @param trailer    trailer link ({@code <trailer>}); {@link #NO_TRAILER} if the NFO has none, and {@code null} if
 *                   it is unknown – an existing entry then stays unchanged when writing
 */
public record MovieNfoSupplement(
        List<NfoActor> actors,
        List<String> directors,
        List<String> writers,
        List<String> studios,
        List<String> countries,
        List<String> tags,
        List<NfoRating> ratings,
        NfoMovieSet set,
        Integer userRating,
        Integer playCount,
        LocalDateTime lastPlayed,
        LocalDateTime dateAdded,
        StreamDetails streams,
        String trailer) {

    /** No trailer; writing removes an existing entry. */
    public static final String NO_TRAILER = "";

    public static final MovieNfoSupplement EMPTY = new MovieNfoSupplement(List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), null, null, null, null, null, StreamDetails.EMPTY);

    public MovieNfoSupplement {
        actors = copy(actors);
        directors = copy(directors);
        writers = copy(writers);
        studios = copy(studios);
        countries = copy(countries);
        tags = copy(tags);
        ratings = copy(ratings);
        streams = streams == null ? StreamDetails.EMPTY : streams;
        trailer = trailer == null ? null : trailer.strip();
    }

    /** Copy with a different trailer link; {@link #NO_TRAILER} or {@code null} as for the constructor. */
    public MovieNfoSupplement withTrailer(String newTrailer) {
        return new MovieNfoSupplement(actors, directors, writers, studios, countries, tags, ratings, set, userRating,
                playCount, lastPlayed, dateAdded, streams, newTrailer);
    }

    /** Details with an unknown trailer; an existing entry stays unchanged when writing. */
    public MovieNfoSupplement(List<NfoActor> actors, List<String> directors, List<String> writers,
            List<String> studios, List<String> countries, List<String> tags, List<NfoRating> ratings, NfoMovieSet set,
            Integer userRating, Integer playCount, LocalDateTime lastPlayed, LocalDateTime dateAdded,
            StreamDetails streams) {
        this(actors, directors, writers, studios, countries, tags, ratings, set, userRating, playCount, lastPlayed,
                dateAdded, streams, null);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
