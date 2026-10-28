package de.tiktak.kmm.movie;

/**
 * Ein Filmset in der Übersicht.
 *
 * @param overview Beschreibung oder {@code null}
 */
public record MovieSetSummary(long id, String name, String overview, String tmdbCollectionId,
                              SetSortMethod sortMethod, long movies) {
}
