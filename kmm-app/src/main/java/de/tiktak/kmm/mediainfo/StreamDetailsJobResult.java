package de.tiktak.kmm.mediainfo;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of reading for a source or new files.
 *
 * @param updated  files with newly applied details
 * @param skipped  files that already had details and were not supposed to be read
 * @param failed   files that ffprobe could not read or whose NFO could not be written
 * @param failures the first error messages
 */
public record StreamDetailsJobResult(long sourceId, int updated, int skipped, int failed,
                                     List<LocalizedText> failures) {

    public StreamDetailsJobResult {
        failures = List.copyOf(failures);
    }
}
