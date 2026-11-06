package de.tiktak.kmm.mediainfo;

/**
 * Which entries of a source are read.
 */
public enum StreamDetailsScope {

    /** Only entries without technical details. */
    MISSING,

    /** All entries; existing details are replaced, languages stay. */
    ALL
}
