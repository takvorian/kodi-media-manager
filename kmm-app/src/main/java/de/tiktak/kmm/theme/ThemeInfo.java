package de.tiktak.kmm.theme;

import java.time.Instant;

/**
 * Die {@code theme.mp3} eines Films oder einer Serie.
 *
 * @param path       Pfad der Datei, auch wenn sie fehlt
 * @param sizeBytes  Größe oder {@code null} ohne Datei
 * @param modifiedAt Änderungszeit oder {@code null} ohne Datei
 */
public record ThemeInfo(boolean exists, String path, Long sizeBytes, Instant modifiedAt) {
}
