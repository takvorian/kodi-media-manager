package de.tiktak.kmm.movie;

import java.time.Instant;

/**
 * Playback state and own rating of a movie.
 *
 * @param filePath     main file; the first part for multi-part movies
 * @param playCount    number of playbacks or {@code null}
 * @param lastPlayedAt last playback or {@code null}
 * @param userRating   own rating 1-10 or {@code null}
 */
public record MoviePlayback(long movieId, String title, String filePath, Integer playCount, Instant lastPlayedAt,
                            Integer userRating) {

    static MoviePlayback of(Movie movie) {
        return new MoviePlayback(movie.getId(), movie.getTitle(), movie.getFilePath(), movie.getPlayCount(),
                movie.getLastPlayedAt(), movie.getUserRating());
    }
}
