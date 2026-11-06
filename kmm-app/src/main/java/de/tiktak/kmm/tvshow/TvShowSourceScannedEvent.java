package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * A scan has reconciled the TV shows of a source with the file system.
 *
 * @param added             newly found TV shows and episodes
 * @param newWithoutStreams IDs of newly found episodes without technical details in the NFO
 * @param showsWithNewEpisodes IDs of the TV shows with newly found episodes
 */
public record TvShowSourceScannedEvent(long sourceId, int added, List<Long> newWithoutStreams,
                                       List<Long> showsWithNewEpisodes) {

    public TvShowSourceScannedEvent {
        newWithoutStreams = List.copyOf(newWithoutStreams);
        showsWithNewEpisodes = List.copyOf(showsWithNewEpisodes);
    }

    public TvShowSourceScannedEvent(long sourceId, int added, List<Long> newWithoutStreams) {
        this(sourceId, added, newWithoutStreams, List.of());
    }

    public TvShowSourceScannedEvent(long sourceId, int added) {
        this(sourceId, added, List.of());
    }
}
