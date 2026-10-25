package de.tiktak.kmm.movie;

/**
 * Ergebnis eines Quellen-Scans.
 *
 * @param added     neu gefundene Filme
 * @param updated   bekannte Filme, deren NFO sich geändert hat und neu eingelesen wurde
 * @param removed   Filme, deren Datei nicht mehr existiert
 * @param unchanged bekannte Filme ohne Änderung
 * @param nfoErrors NFOs, die nicht gelesen werden konnten (die Filme bleiben trotzdem erhalten)
 */
public record ScanResult(long sourceId, int added, int updated, int removed, int unchanged, int nfoErrors) {
}
