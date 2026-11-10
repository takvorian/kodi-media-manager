package de.tiktak.kmm.movie;

/**
 * A movie was removed from the library. Carries what Kodi needs to find it, because the entry is gone.
 *
 * @param title    title of the movie, for messages
 * @param filePath main file of the movie (the first part for stacks, the marker file for discs)
 */
public record MovieRemovedEvent(String title, String filePath) {
}
