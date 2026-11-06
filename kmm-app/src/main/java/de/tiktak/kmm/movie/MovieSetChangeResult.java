package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of a change to a movie set.
 *
 * @param changed   movies whose set data changed
 * @param unchanged movies without a change
 * @param failures  failure messages, at most {@link MovieSetJobService#MAX_LISTED_FAILURES}
 */
public record MovieSetChangeResult(int changed, int unchanged, int failed, List<LocalizedText> failures) {

    public MovieSetChangeResult {
        failures = List.copyOf(failures);
    }
}
