package de.tiktak.kmm.kodi;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of taking over the watched status of a whole source.
 *
 * @param host      name of the Kodi device that was asked
 * @param kept      entries for which KMM knows more than Kodi and keeps its state
 * @param notInKodi entries that Kodi does not know
 * @param failures  error messages, at most {@link KodiPlaybackService#MAX_LISTED_FAILURES}
 */
public record KodiPlaybackImportResult(long sourceId, String host, int updated, int unchanged, int kept,
                                       int notInKodi, int failed, List<LocalizedText> failures) {

    public KodiPlaybackImportResult {
        failures = List.copyOf(failures);
    }
}
