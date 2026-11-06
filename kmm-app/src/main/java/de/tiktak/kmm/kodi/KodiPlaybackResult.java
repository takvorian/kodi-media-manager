package de.tiktak.kmm.kodi;

import java.time.Instant;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of taking over the watched status and the user rating of a movie or an episode.
 *
 * @param host         name of the Kodi device that was asked
 * @param message      readable description for the user interface
 * @param playCount    number of plays in KMM afterwards, or {@code null}
 * @param lastPlayedAt last play in KMM afterwards, or {@code null}
 * @param userRating   user rating in KMM afterwards, or {@code null}
 */
public record KodiPlaybackResult(String host, KodiPlaybackOutcome outcome, LocalizedText message, Integer playCount,
                                 Instant lastPlayedAt, Integer userRating) {
}
