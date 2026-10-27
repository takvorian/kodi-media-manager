package de.tiktak.kmm.mediainfo;

import java.util.List;

/**
 * Ergebnis des Auslesens für eine Quelle oder neue Dateien.
 *
 * @param updated  Dateien mit neu übernommenen Angaben
 * @param skipped  Dateien, die schon Angaben hatten und nicht ausgelesen werden sollten
 * @param failed   Dateien, die ffprobe nicht lesen konnte oder deren NFO nicht geschrieben werden konnte
 * @param failures die ersten Fehlermeldungen
 */
public record StreamDetailsJobResult(long sourceId, int updated, int skipped, int failed, List<String> failures) {

    public StreamDetailsJobResult {
        failures = List.copyOf(failures);
    }
}
