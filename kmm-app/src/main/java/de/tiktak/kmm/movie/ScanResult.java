package de.tiktak.kmm.movie;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tiktak.kmm.source.ScanDetails;

/**
 * Result of a source scan.
 *
 * @param added                newly found movies
 * @param updated              known movies whose NFO has changed and was re-read or whose video file was
 *                             replaced
 * @param removed              movies whose file no longer exists
 * @param unchanged            known movies without changes
 * @param nfoErrors            NFOs that could not be read (the movies are kept anyway)
 * @param details              the affected movies, truncated
 * @param newWithoutStreams    IDs of the newly found movies without stream details in the NFO and of the
 *                             movies whose video file was replaced; ffprobe reads them if enabled
 */
public record ScanResult(long sourceId, int added, int updated, int removed, int unchanged, int nfoErrors,
        ScanDetails details, @JsonIgnore List<Long> newWithoutStreams) {

    public ScanResult {
        details = details == null ? ScanDetails.EMPTY : details;
        newWithoutStreams = newWithoutStreams == null ? List.of()
                : newWithoutStreams.stream().filter(Objects::nonNull).toList();
    }

    public ScanResult(long sourceId, int added, int updated, int removed, int unchanged, int nfoErrors) {
        this(sourceId, added, updated, removed, unchanged, nfoErrors, ScanDetails.EMPTY, List.of());
    }
}
