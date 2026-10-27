package de.tiktak.kmm.movie;

import java.time.Instant;

/**
 * Wiedergabestand und eigene Bewertung eines Films.
 *
 * @param filePath     Hauptdatei, bei mehrteiligen Filmen der erste Teil
 * @param playCount    Anzahl der Wiedergaben oder {@code null}
 * @param lastPlayedAt letzte Wiedergabe oder {@code null}
 * @param userRating   eigene Bewertung 1–10 oder {@code null}
 */
public record MoviePlayback(long movieId, String title, String filePath, Integer playCount, Instant lastPlayedAt,
                            Integer userRating) {

    static MoviePlayback of(Movie movie) {
        return new MoviePlayback(movie.getId(), movie.getTitle(), movie.getFilePath(), movie.getPlayCount(),
                movie.getLastPlayedAt(), movie.getUserRating());
    }
}
