package de.tiktak.kmm.scanner.removal;

import java.nio.file.Path;
import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Outcome of {@link FileRemoval#delete}.
 *
 * @param deleted  files that were deleted
 * @param failures one message per file that could not be deleted
 */
public record Deletion(List<Path> deleted, List<LocalizedText> failures) {

    public Deletion {
        deleted = List.copyOf(deleted);
        failures = List.copyOf(failures);
    }
}
