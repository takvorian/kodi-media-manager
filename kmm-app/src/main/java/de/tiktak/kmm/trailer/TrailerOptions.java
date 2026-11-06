package de.tiktak.kmm.trailer;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Trailer suggestions, the best first.
 *
 * @param warnings hints about providers that are not set up or failed
 */
public record TrailerOptions(List<TrailerOption> candidates, List<LocalizedText> warnings) {

    public TrailerOptions {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }
}
