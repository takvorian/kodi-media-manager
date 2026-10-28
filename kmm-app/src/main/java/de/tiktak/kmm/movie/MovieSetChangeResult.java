package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Ergebnis einer Änderung an einem Filmset.
 *
 * @param changed   Filme, deren Set-Angaben sich geändert haben
 * @param unchanged Filme ohne Änderung
 * @param failures  Fehlermeldungen, höchstens {@link MovieSetJobService#MAX_LISTED_FAILURES}
 */
public record MovieSetChangeResult(int changed, int unchanged, int failed, List<String> failures) {

    public MovieSetChangeResult {
        failures = List.copyOf(failures);
    }
}
