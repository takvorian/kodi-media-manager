package de.tiktak.kmm.mediainfo;

/**
 * Welche Einträge einer Quelle ausgelesen werden.
 */
public enum StreamDetailsScope {

    /** Nur Einträge ohne technische Angaben. */
    MISSING,

    /** Alle Einträge; vorhandene Angaben werden ersetzt, Sprachen bleiben. */
    ALL
}
