package de.tiktak.kmm.tag;

import java.util.List;

/**
 * Ergebnis einer Tag-Änderung.
 *
 * @param changed   Filme und Serien mit geänderten Tags
 * @param unchanged Filme und Serien, bei denen sich nichts änderte
 * @param failures  Fehlermeldungen, höchstens {@link TagService#MAX_LISTED_FAILURES}
 */
public record TagChangeResult(int changed, int unchanged, int failed, List<String> failures) {

    public TagChangeResult {
        failures = List.copyOf(failures);
    }
}
