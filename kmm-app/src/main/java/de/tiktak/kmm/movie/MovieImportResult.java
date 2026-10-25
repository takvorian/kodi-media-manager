package de.tiktak.kmm.movie;

/**
 * Ergebnis der Übernahme eines Films.
 *
 * @param created    ob der Film neu angelegt wurde ({@code false}: vorhandener Film mit derselben Datei)
 * @param nfoPresent ob neben der Datei eine NFO liegt
 */
public record MovieImportResult(boolean created, boolean nfoPresent) {
}
