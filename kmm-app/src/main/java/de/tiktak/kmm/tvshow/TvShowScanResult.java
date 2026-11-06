package de.tiktak.kmm.tvshow;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tiktak.kmm.source.ScanDetails;

/**
 * Result of a TV show scan. {@code added} to {@code unchanged} count episodes, so that the
 * user interface can display movie and TV show scans alike.
 *
 * @param showsAdded        newly found TV shows
 * @param showsRemoved      vanished TV shows
 * @param nfoErrors         unreadable NFOs
 * @param unrecognizedFiles video files without a recognizable season and episode (truncated)
 * @param details           the affected TV shows and episodes, truncated
 * @param newWithoutStreams IDs of newly found episodes without technical details in the NFO
 * @param showsWithNewEpisodes IDs of the TV shows with newly found episodes
 */
public record TvShowScanResult(
        long sourceId,
        int showsAdded,
        int showsRemoved,
        int added,
        int updated,
        int removed,
        int unchanged,
        int nfoErrors,
        List<String> unrecognizedFiles,
        ScanDetails details,
        @JsonIgnore List<Long> newWithoutStreams,
        @JsonIgnore List<Long> showsWithNewEpisodes) {

    public TvShowScanResult {
        unrecognizedFiles = List.copyOf(unrecognizedFiles);
        details = details == null ? ScanDetails.EMPTY : details;
        newWithoutStreams = newWithoutStreams == null ? List.of()
                : newWithoutStreams.stream().filter(Objects::nonNull).toList();
        showsWithNewEpisodes = showsWithNewEpisodes == null ? List.of() : List.copyOf(showsWithNewEpisodes);
    }

    public TvShowScanResult(long sourceId, int showsAdded, int showsRemoved, int added, int updated, int removed,
            int unchanged, int nfoErrors, List<String> unrecognizedFiles) {
        this(sourceId, showsAdded, showsRemoved, added, updated, removed, unchanged, nfoErrors, unrecognizedFiles,
                ScanDetails.EMPTY, List.of(), List.of());
    }
}
