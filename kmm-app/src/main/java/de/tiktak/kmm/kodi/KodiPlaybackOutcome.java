package de.tiktak.kmm.kodi;

/**
 * Result of taking over the watched status of a movie or an episode from Kodi.
 */
public enum KodiPlaybackOutcome {
    /** KMM took over the state from Kodi into NFO and database. */
    UPDATED,
    /** The state in KMM already matches the one in Kodi. */
    UNCHANGED,
    /**
     * KMM knows more plays or a more recent last play than Kodi. KMM does not reset anything, the state in KMM
     * stays.
     */
    KEPT,
    /** Kodi does not know the entry; the state in KMM stays unchanged. */
    NOT_IN_KODI,
    /** The NFO could not be written. */
    FAILED
}
