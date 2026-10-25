package de.tiktak.kmm.movie;

/**
 * Ein Scan hat die Filme einer Quelle mit dem Dateisystem abgeglichen.
 *
 * @param added   neu gefundene Filme
 * @param removed verschwundene Filme
 */
public record MovieSourceScannedEvent(long sourceId, int added, int removed) {
}
