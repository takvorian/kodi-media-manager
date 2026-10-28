package de.tiktak.kmm.kodi;

/**
 * Ergebnis eines Abgleichs mit einem Kodi-Gerät.
 */
public enum KodiSyncOutcome {
    /** Kodi hat NFO und Bilder des Films neu eingelesen. */
    REFRESHED,
    /** Kodi durchsucht ein Verzeichnis nach neuen Filmen. */
    SCAN_STARTED,
    /** Kodi hat geänderte Angaben übernommen, etwa Titel und Beschreibung eines Filmsets. */
    UPDATED,
    /** Kodi kennt den Eintrag (noch) nicht; er kommt mit dem nächsten Einlesen. */
    NOT_IN_KODI,
    /** Für den Pfad gibt es keine Zuordnung zu einem Kodi-Pfad. */
    NOT_MAPPED,
    /** Kodi war nicht erreichbar oder meldete einen Fehler. */
    FAILED
}
