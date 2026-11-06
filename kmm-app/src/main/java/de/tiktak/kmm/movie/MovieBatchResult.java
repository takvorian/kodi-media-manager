package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of a batch edit.
 *
 * @param failures the first failures with movie ID, at most {@link MovieBatchService#MAX_LISTED_FAILURES}
 */
public record MovieBatchResult(int changed, int unchanged, int failed, List<LocalizedText> failures) {

    public MovieBatchResult {
        failures = List.copyOf(failures);
    }
}
