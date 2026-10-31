package de.tiktak.kmm.source;

import de.tiktak.kmm.plugin.api.MediaType;

/**
 * Ein Eintrag im Bericht eines Scans.
 *
 * @param type  Art des Eintrags
 * @param id    ID in der Bibliothek; {@code null} bei entfernten Einträgen
 * @param label Bezeichnung für die Anzeige
 */
public record ScanEntry(MediaType type, Long id, String label) {
}
