package de.tiktak.kmm.nfo;

import java.time.LocalDateTime;

/**
 * Playback state and own rating of a movie or an episode, as Kodi keeps them in its library.
 *
 * @param playCount  number of playbacks, or {@code null} to remove the entry
 * @param lastPlayed last playback, or {@code null} to remove the entry
 * @param userRating own rating 1–10, or {@code null} to keep the existing entry
 */
public record NfoPlayback(Integer playCount, LocalDateTime lastPlayed, Integer userRating) {
}
