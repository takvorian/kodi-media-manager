package de.tiktak.kmm.source;

/**
 * Ergebnis der Übernahme einer Quelle aus einer Migration.
 *
 * @param created ob die Quelle neu angelegt wurde ({@code false}: vorhandene Quelle mit demselben Verzeichnis)
 */
public record ImportedSource(MediaSource source, boolean created) {
}
