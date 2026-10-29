package de.tiktak.kmm.trailer;

import java.util.List;

/**
 * Trailer-Vorschläge, die besten zuerst.
 *
 * @param warnings Hinweise zu nicht eingerichteten oder fehlerhaften Anbietern
 */
public record TrailerOptions(List<TrailerOption> candidates, List<String> warnings) {

    public TrailerOptions {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }
}
