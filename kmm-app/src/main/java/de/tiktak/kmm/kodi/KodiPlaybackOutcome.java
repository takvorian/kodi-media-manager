package de.tiktak.kmm.kodi;

/**
 * Ergebnis der Übernahme des Gesehen-Status eines Films oder einer Episode aus Kodi.
 */
public enum KodiPlaybackOutcome {
    /** KMM hat den Stand aus Kodi in NFO und Datenbank übernommen. */
    UPDATED,
    /** Der Stand in KMM entspricht bereits dem in Kodi. */
    UNCHANGED,
    /** Kodi kennt den Eintrag nicht; der Stand in KMM bleibt unverändert. */
    NOT_IN_KODI,
    /** Die NFO konnte nicht geschrieben werden. */
    FAILED
}
