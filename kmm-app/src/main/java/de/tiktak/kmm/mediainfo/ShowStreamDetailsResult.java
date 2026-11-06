package de.tiktak.kmm.mediainfo;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of reading the episodes of a TV show after scraping.
 *
 * @param updated  files with newly applied details
 * @param failures the error messages of the files that could not be read
 */
public record ShowStreamDetailsResult(int updated, List<LocalizedText> failures) {

    public ShowStreamDetailsResult {
        failures = List.copyOf(failures);
    }
}
