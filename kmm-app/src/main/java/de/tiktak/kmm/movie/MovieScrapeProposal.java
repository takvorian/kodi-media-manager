package de.tiktak.kmm.movie;

import de.tiktak.kmm.plugin.api.CollectionReference;

/**
 * Suggestion derived from a search match.
 *
 * @param metadata   existing metadata, overlaid with the data of the match
 * @param collection the movie's collection at the provider or {@code null}; after saving,
 *                   {@code POST /api/v1/movies/{id}/collection} assigns the movie to its set
 */
public record MovieScrapeProposal(MovieMetadata metadata, CollectionReference collection) {
}
