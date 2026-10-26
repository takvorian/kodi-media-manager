package de.tiktak.kmm.tvshow;

/**
 * Ein Scan hat die Serien einer Quelle mit dem Dateisystem abgeglichen.
 *
 * @param added neu gefundene Serien und Episoden
 */
public record TvShowSourceScannedEvent(long sourceId, int added) {
}
