package de.tiktak.kmm.artwork;

import java.nio.file.Path;

/**
 * Ein Bild eines Filmsets für Kodi.
 *
 * @param kodiName Kodis Bezeichnung der Bildart, z. B. {@code poster}
 * @param file     vorhandene Datei oder {@code null}, wenn das Bild fehlt
 */
public record MovieSetImage(String kodiName, Path file) {
}
