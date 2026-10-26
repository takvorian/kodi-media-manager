package de.tiktak.kmm.tvshow;

/**
 * Ergebnis der Übernahme einer Serie oder Episode.
 *
 * @param created    ob sie neu angelegt wurde ({@code false}: vorhandener Eintrag mit demselben Pfad)
 * @param nfoPresent ob die zugehörige NFO vorhanden ist
 */
public record TvImportResult(boolean created, boolean nfoPresent) {
}
