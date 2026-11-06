package de.tiktak.kmm.tvshow;

/**
 * The {@code tvshow.nfo} or images of a TV show or its seasons were written or deleted.
 */
public record TvShowFilesChangedEvent(long showId) {
}
