package de.tiktak.kmm.kodi;

/**
 * Learns how far a sync with many steps, e.g. all episodes of a TV show, has come.
 */
@FunctionalInterface
interface KodiProgress {

    /** For callers that do not show any progress. */
    KodiProgress NONE = (done, total) -> {
    };

    /**
     * @param done  steps done so far
     * @param total all steps
     */
    void report(int done, int total);
}
