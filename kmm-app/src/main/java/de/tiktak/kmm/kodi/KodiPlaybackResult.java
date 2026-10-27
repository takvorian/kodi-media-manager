package de.tiktak.kmm.kodi;

import java.time.Instant;

/**
 * Ergebnis der Übernahme von Gesehen-Status und eigener Bewertung eines Films oder einer Episode.
 *
 * @param host         Name des befragten Kodi-Geräts
 * @param message      lesbare Beschreibung für die Oberfläche
 * @param playCount    Anzahl der Wiedergaben in KMM danach oder {@code null}
 * @param lastPlayedAt letzte Wiedergabe in KMM danach oder {@code null}
 * @param userRating   eigene Bewertung in KMM danach oder {@code null}
 */
public record KodiPlaybackResult(String host, KodiPlaybackOutcome outcome, String message, Integer playCount,
                                 Instant lastPlayedAt, Integer userRating) {
}
