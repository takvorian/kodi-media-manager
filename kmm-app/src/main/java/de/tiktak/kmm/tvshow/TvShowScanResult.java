package de.tiktak.kmm.tvshow;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Ergebnis eines Serien-Scans. {@code added} bis {@code unchanged} zählen Episoden, damit die
 * Oberfläche Film- und Serien-Scans gleich darstellen kann.
 *
 * @param showsAdded        neu gefundene Serien
 * @param showsRemoved      verschwundene Serien
 * @param nfoErrors         unlesbare NFOs
 * @param unrecognizedFiles Videodateien ohne erkennbare Staffel und Episode (gekürzt)
 * @param newWithoutStreams IDs der neu gefundenen Episoden ohne technische Angaben in der NFO
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
        @JsonIgnore List<Long> newWithoutStreams) {

    public TvShowScanResult {
        unrecognizedFiles = List.copyOf(unrecognizedFiles);
        newWithoutStreams = newWithoutStreams == null ? List.of()
                : newWithoutStreams.stream().filter(Objects::nonNull).toList();
    }

    public TvShowScanResult(long sourceId, int showsAdded, int showsRemoved, int added, int updated, int removed,
            int unchanged, int nfoErrors, List<String> unrecognizedFiles) {
        this(sourceId, showsAdded, showsRemoved, added, updated, removed, unchanged, nfoErrors, unrecognizedFiles,
                List.of());
    }
}
