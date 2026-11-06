package de.tiktak.kmm.movie;

/**
 * Result of taking over a movie.
 *
 * @param created    whether the movie was newly created ({@code false}: existing movie with the same file)
 * @param nfoPresent whether an NFO lies next to the file
 */
public record MovieImportResult(boolean created, boolean nfoPresent) {
}
