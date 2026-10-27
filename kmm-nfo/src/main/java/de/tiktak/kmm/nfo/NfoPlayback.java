package de.tiktak.kmm.nfo;

import java.time.LocalDateTime;

/**
 * Wiedergabestand und eigene Bewertung eines Films oder einer Episode, wie sie Kodi in seiner Bibliothek
 * führt.
 *
 * @param playCount  Anzahl der Wiedergaben oder {@code null}, um die Angabe zu entfernen
 * @param lastPlayed letzte Wiedergabe oder {@code null}, um die Angabe zu entfernen
 * @param userRating eigene Bewertung 1–10 oder {@code null}, um die vorhandene Angabe zu behalten
 */
public record NfoPlayback(Integer playCount, LocalDateTime lastPlayed, Integer userRating) {
}
