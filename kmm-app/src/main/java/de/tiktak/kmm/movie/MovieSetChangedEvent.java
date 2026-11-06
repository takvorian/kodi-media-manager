package de.tiktak.kmm.movie;

/**
 * Title, description or images of a movie set have changed, for example so that Kodi picks them up.
 *
 * @param previousName previous name, to find the set in Kodi
 */
public record MovieSetChangedEvent(long setId, String previousName) {
}
