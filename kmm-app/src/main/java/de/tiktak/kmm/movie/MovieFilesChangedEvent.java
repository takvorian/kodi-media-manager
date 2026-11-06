package de.tiktak.kmm.movie;

/**
 * The companion files of a movie (NFO, images) were changed. Published after saving,
 * e.g. so that Kodi rescans the movie.
 */
public record MovieFilesChangedEvent(long movieId) {
}
