package de.tiktak.kmm.kodi;

/**
 * Result of a sync with a Kodi device.
 */
public enum KodiSyncOutcome {
    /** Kodi read the NFO and images of the movie again. */
    REFRESHED,
    /** Kodi scans a directory for new movies. */
    SCAN_STARTED,
    /** Kodi took over changed details, e.g. title and overview of a movie set. */
    UPDATED,
    /** Kodi does not know the entry (yet); it arrives with the next scan. */
    NOT_IN_KODI,
    /** There is no mapping of the path to a Kodi path. */
    NOT_MAPPED,
    /** The path lies outside all sources of the device; a scan will probably find nothing there. */
    NO_KODI_SOURCE,
    /** Kodi could not be reached or reported an error. */
    FAILED
}
