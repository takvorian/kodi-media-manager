package de.tiktak.kmm.theme;

import java.util.List;

/**
 * Titelmusik-Vorschläge, Treffer mit gleichem Titel zuerst.
 *
 * @param warnings Hinweise zu nicht eingerichteten oder fehlerhaften Anbietern
 */
public record ThemeOptions(List<ThemeOption> candidates, List<String> warnings) {

    public ThemeOptions {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }
}
