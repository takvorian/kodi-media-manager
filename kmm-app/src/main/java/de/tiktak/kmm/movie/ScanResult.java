package de.tiktak.kmm.movie;

/**
 * Ergebnis eines Quellen-Scans.
 *
 * @param added     neu gefundene Filme
 * @param removed   Filme, deren Datei nicht mehr existiert
 * @param unchanged bereits bekannte Filme
 */
public record ScanResult(long sourceId, int added, int removed, int unchanged) {
}
