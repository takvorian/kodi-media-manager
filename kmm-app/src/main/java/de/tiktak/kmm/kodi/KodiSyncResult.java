package de.tiktak.kmm.kodi;

import java.time.Instant;

/**
 * Ergebnis eines Abgleichs mit einem Kodi-Gerät.
 *
 * @param host    Name des Geräts
 * @param message lesbare Beschreibung für die Oberfläche
 */
public record KodiSyncResult(long hostId, String host, KodiSyncOutcome outcome, String message, Instant at) {

    boolean failed() {
        return outcome == KodiSyncOutcome.FAILED || outcome == KodiSyncOutcome.NOT_MAPPED
                || outcome == KodiSyncOutcome.NO_KODI_SOURCE;
    }
}
