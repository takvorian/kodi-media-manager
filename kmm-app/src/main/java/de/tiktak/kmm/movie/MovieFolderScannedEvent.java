package de.tiktak.kmm.movie;

import java.util.List;

/**
 * KMM has reconciled the movies in the folder of one movie with the file system.
 *
 * @param directory         the scanned folder
 * @param added             newly found movies
 * @param removed           movies that have disappeared
 * @param newWithoutStreams IDs of the movies whose technical details are to be read, as for a source scan
 */
public record MovieFolderScannedEvent(long sourceId, String directory, int added, int removed,
        List<Long> newWithoutStreams) {

    public MovieFolderScannedEvent {
        newWithoutStreams = List.copyOf(newWithoutStreams);
    }

    /** Kodi only needs to look at the folder if movies were added or removed. */
    public boolean moviesChanged() {
        return added > 0 || removed > 0;
    }
}
