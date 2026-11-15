package de.tiktak.kmm.tvshow;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code PUT /api/v1/tvshows/{showId}/episode-ordering}.
 *
 * @param scraperId the scraper whose order is chosen, or {@code null} together with {@code orderId} for the order
 *                  from the settings
 * @param orderId   the provider's key of the order, from {@code GET .../episode-orders}
 * @param refetch   whether titles and texts of all episodes are fetched again in the new order right away
 */
public record TvShowEpisodeOrderingChange(
        @Pattern(regexp = "[a-z0-9-]{1,40}") String scraperId,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String orderId,
        boolean refetch) {

    /** Both or neither. */
    @AssertTrue
    boolean isComplete() {
        return (scraperId == null) == (orderId == null);
    }

    boolean resetsToSettings() {
        return scraperId == null;
    }
}
