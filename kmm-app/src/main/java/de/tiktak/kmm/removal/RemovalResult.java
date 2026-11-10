package de.tiktak.kmm.removal;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of a removal job.
 *
 * @param removedEntries number of movies, TV shows or episodes removed from the library
 * @param deletedFiles   number of deleted files
 * @param failures       one message per file that could not be deleted
 */
public record RemovalResult(int removedEntries, int deletedFiles, List<LocalizedText> failures) {

    public RemovalResult {
        failures = List.copyOf(failures);
    }
}
