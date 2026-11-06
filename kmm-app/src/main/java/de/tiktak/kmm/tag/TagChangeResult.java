package de.tiktak.kmm.tag;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of a tag change.
 *
 * @param changed   movies and TV shows with changed tags
 * @param unchanged movies and TV shows where nothing changed
 * @param failures  failure messages, at most {@link TagService#MAX_LISTED_FAILURES}
 */
public record TagChangeResult(int changed, int unchanged, int failed, List<LocalizedText> failures) {

    public TagChangeResult {
        failures = List.copyOf(failures);
    }
}
