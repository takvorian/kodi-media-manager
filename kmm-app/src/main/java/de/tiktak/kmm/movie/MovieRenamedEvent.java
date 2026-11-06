package de.tiktak.kmm.movie;

/**
 * Folder or files of a movie were renamed. Afterwards Kodi only knows the old paths.
 *
 * @param previousDirectory folder of the movie before
 * @param directory         folder of the movie now; equal to {@code previousDirectory} if only files were
 *                          renamed
 */
public record MovieRenamedEvent(long movieId, long sourceId, String previousDirectory, String directory) {
}
