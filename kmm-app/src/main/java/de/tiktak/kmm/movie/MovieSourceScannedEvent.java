package de.tiktak.kmm.movie;

import java.util.List;

/**
 * A scan has reconciled the movies of a source with the file system.
 *
 * @param added             newly found movies
 * @param removed           movies that have disappeared
 * @param newWithoutStreams IDs of the newly found movies without stream details in the NFO
 */
public record MovieSourceScannedEvent(long sourceId, int added, int removed, List<Long> newWithoutStreams) {

    public MovieSourceScannedEvent {
        newWithoutStreams = List.copyOf(newWithoutStreams);
    }

    public MovieSourceScannedEvent(long sourceId, int added, int removed) {
        this(sourceId, added, removed, List.of());
    }
}
