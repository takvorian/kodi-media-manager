package de.tiktak.kmm.artwork;

import java.util.List;

/**
 * Ergebnis von „fehlende Bilder füllen“ für einen Film.
 *
 * @param saved    Anzahl neu gespeicherter Bilder
 * @param warnings Hinweise zu Anbietern oder Bildern, die nicht geladen werden konnten
 */
public record ArtworkFillResult(int saved, List<String> warnings) {

    public ArtworkFillResult {
        warnings = List.copyOf(warnings);
    }
}
