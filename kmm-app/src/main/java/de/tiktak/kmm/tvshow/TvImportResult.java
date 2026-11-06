package de.tiktak.kmm.tvshow;

/**
 * Result of taking over a TV show or episode.
 *
 * @param created    whether it was newly created ({@code false}: existing entry with the same path)
 * @param nfoPresent whether the associated NFO exists
 */
public record TvImportResult(boolean created, boolean nfoPresent) {
}
