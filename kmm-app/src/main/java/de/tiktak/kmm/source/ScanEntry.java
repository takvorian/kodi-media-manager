package de.tiktak.kmm.source;

import de.tiktak.kmm.plugin.api.MediaType;

/**
 * An entry in the report of a scan.
 *
 * @param type  kind of entry
 * @param id    ID in the library; {@code null} for removed entries
 * @param label label for display
 */
public record ScanEntry(MediaType type, Long id, String label) {
}
