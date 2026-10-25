package de.tiktak.kmm.movie;

/**
 * Die Begleitdateien eines Films (NFO, Bilder) wurden geändert. Wird nach dem Speichern
 * veröffentlicht, z. B. damit Kodi den Film neu einliest.
 */
public record MovieFilesChangedEvent(long movieId) {
}
