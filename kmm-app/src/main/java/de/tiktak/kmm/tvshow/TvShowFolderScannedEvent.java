package de.tiktak.kmm.tvshow;

import java.util.List;
import java.util.OptionalInt;

/**
 * A scan has reconciled one TV show, or one of its seasons, with its folder.
 *
 * @param season            the scanned season; empty for the whole TV show
 * @param added             newly found episodes
 * @param removed           episodes whose files are gone
 * @param newWithoutStreams IDs of newly found episodes without technical details in the NFO
 */
public record TvShowFolderScannedEvent(long sourceId, long showId, OptionalInt season, int added, int removed,
                                       List<Long> newWithoutStreams) {

    public TvShowFolderScannedEvent {
        newWithoutStreams = List.copyOf(newWithoutStreams);
    }

    /** Whether episodes came or went, so that Kodi has something to catch up on. */
    public boolean episodesChanged() {
        return added + removed > 0;
    }
}
