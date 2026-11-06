package de.tiktak.kmm.movie;

/**
 * A movie set in the overview.
 *
 * @param overview description or {@code null}
 */
public record MovieSetSummary(long id, String name, String overview, String tmdbCollectionId,
                              SetSortMethod sortMethod, long movies) {
}
