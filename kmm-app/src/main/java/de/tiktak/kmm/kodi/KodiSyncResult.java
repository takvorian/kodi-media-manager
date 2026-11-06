package de.tiktak.kmm.kodi;

import java.time.Instant;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of a sync with a Kodi device.
 *
 * @param host    name of the device
 * @param message readable description for the user interface
 */
public record KodiSyncResult(long hostId, String host, KodiSyncOutcome outcome, LocalizedText message, Instant at) {

    boolean failed() {
        return outcome == KodiSyncOutcome.FAILED || outcome == KodiSyncOutcome.NOT_MAPPED
                || outcome == KodiSyncOutcome.NO_KODI_SOURCE;
    }
}
