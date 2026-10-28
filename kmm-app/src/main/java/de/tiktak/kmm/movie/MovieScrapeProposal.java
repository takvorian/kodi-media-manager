package de.tiktak.kmm.movie;

import de.tiktak.kmm.plugin.api.CollectionReference;

/**
 * Vorschlag aus einem Suchtreffer.
 *
 * @param metadata   vorhandene Metadaten, überlagert mit den Daten des Treffers
 * @param collection Sammlung des Films beim Anbieter oder {@code null}; nach dem Speichern ordnet
 *                   {@code POST /api/v1/movies/{id}/collection} den Film ihrem Set zu
 */
public record MovieScrapeProposal(MovieMetadata metadata, CollectionReference collection) {
}
