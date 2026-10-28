package de.tiktak.kmm.movie;

/**
 * Titel oder Beschreibung eines Filmsets haben sich geändert, etwa damit Kodi sie übernimmt.
 *
 * @param previousName Name vorher, um das Set in Kodi zu finden
 */
public record MovieSetChangedEvent(long setId, String previousName) {
}
