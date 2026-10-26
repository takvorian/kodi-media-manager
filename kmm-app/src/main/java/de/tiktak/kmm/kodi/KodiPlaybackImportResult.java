package de.tiktak.kmm.kodi;

import java.util.List;

/**
 * Ergebnis der Übernahme des Gesehen-Status einer ganzen Quelle.
 *
 * @param host      Name des befragten Kodi-Geräts
 * @param notInKodi Einträge, die Kodi nicht kennt
 * @param failures  Fehlermeldungen, höchstens {@link KodiPlaybackService#MAX_LISTED_FAILURES}
 */
public record KodiPlaybackImportResult(long sourceId, String host, int updated, int unchanged, int notInKodi,
                                       int failed, List<String> failures) {

    public KodiPlaybackImportResult {
        failures = List.copyOf(failures);
    }
}
