package de.tiktak.kmm.mediainfo;

import java.util.List;

/**
 * Ergebnis des Auslesens der Episoden einer Serie nach dem Scrapen.
 *
 * @param updated  Dateien mit neu übernommenen Angaben
 * @param failures die Fehlermeldungen der Dateien, die nicht gelesen werden konnten
 */
public record ShowStreamDetailsResult(int updated, List<String> failures) {

    public ShowStreamDetailsResult {
        failures = List.copyOf(failures);
    }
}
