package de.tiktak.kmm.tvshow;

/**
 * A TV show with all its episodes was removed from the library. Carries what Kodi needs to find it, because the
 * entry is gone.
 *
 * @param title     title of the TV show, for messages
 * @param directory TV show folder
 */
public record TvShowRemovedEvent(String title, String directory) {
}
