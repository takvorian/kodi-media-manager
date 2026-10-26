package de.tiktak.kmm.tvshow;

/**
 * Die {@code tvshow.nfo} oder Bilder einer Serie bzw. ihrer Staffeln wurden geschrieben oder gelöscht.
 */
public record TvShowFilesChangedEvent(long showId) {
}
