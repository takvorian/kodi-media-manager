package de.tiktak.kmm.movie;

/**
 * Ordner oder Dateien eines Films wurden umbenannt. Kodi kennt danach nur noch die alten Pfade.
 *
 * @param previousDirectory Ordner des Films vorher
 * @param directory         Ordner des Films jetzt; gleich {@code previousDirectory}, wenn nur Dateien umbenannt
 *                          wurden
 */
public record MovieRenamedEvent(long movieId, long sourceId, String previousDirectory, String directory) {
}
