package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Result of taking over episode data from the online search.
 *
 * @param updated     episodes whose empty fields were filled in
 * @param unchanged   episodes the provider knows but that were missing nothing
 * @param unmatched   library episodes the provider does not know
 * @param failedFiles NFOs that could not be written; their episodes stayed unchanged
 */
public record EpisodeFillResult(int updated, int unchanged, int unmatched, List<String> failedFiles) {

    public EpisodeFillResult {
        failedFiles = List.copyOf(failedFiles);
    }
}
