package de.tiktak.kmm.movie;

/**
 * The scan found a new video file that replaces the vanished one of a movie in place, e.g. an AVI converted to MKV.
 * KMM keeps the entry; Kodi only knows the old file.
 *
 * @param previousFilePath the main file before
 */
public record MovieFileReplacedEvent(long movieId, long sourceId, String previousFilePath) {
}
