package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Ergebnis einer Stapel-Bearbeitung.
 *
 * @param failures die ersten Fehler mit Film-ID, höchstens {@link MovieBatchService#MAX_LISTED_FAILURES}
 */
public record MovieBatchResult(int changed, int unchanged, int failed, List<String> failures) {

    public MovieBatchResult {
        failures = List.copyOf(failures);
    }
}
