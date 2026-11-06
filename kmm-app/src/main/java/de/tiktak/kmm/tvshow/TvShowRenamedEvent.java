package de.tiktak.kmm.tvshow;

/**
 * TV show folder, season folders or episode files of a TV show were renamed. Kodi afterwards only knows the
 * old paths.
 *
 * @param previousDirectory TV show folder before
 * @param directory         TV show folder now; equal to {@code previousDirectory} if it stayed
 */
public record TvShowRenamedEvent(long showId, long sourceId, String previousDirectory, String directory) {
}
