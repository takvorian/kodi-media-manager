package de.tiktak.kmm.theme;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Theme music suggestions, matches with the same title first.
 *
 * @param warnings hints about providers that are not set up or failed
 */
public record ThemeOptions(List<ThemeOption> candidates, List<LocalizedText> warnings) {

    public ThemeOptions {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }
}
