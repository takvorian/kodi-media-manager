package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of “Complete all sets”.
 *
 * @param overviews         sets that got an overview
 * @param images            saved images
 * @param withoutCollection sets without a TMDB ID, for which nothing was searched
 * @param problems          failures and hints, at most {@link MovieSetJobService#MAX_LISTED_FAILURES}
 */
public record MovieSetCompletionResult(int overviews, int images, int withoutCollection,
                                       List<LocalizedText> problems) {

    public MovieSetCompletionResult {
        problems = List.copyOf(problems);
    }
}
