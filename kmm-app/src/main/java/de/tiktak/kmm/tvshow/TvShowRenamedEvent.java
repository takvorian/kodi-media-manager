package de.tiktak.kmm.tvshow;

/**
 * Serienordner, Staffelordner oder Episodendateien einer Serie wurden umbenannt. Kodi kennt danach nur noch die
 * alten Pfade.
 *
 * @param previousDirectory Serienordner vorher
 * @param directory         Serienordner jetzt; gleich {@code previousDirectory}, wenn er blieb
 */
public record TvShowRenamedEvent(long showId, long sourceId, String previousDirectory, String directory) {
}
