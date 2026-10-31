package de.tiktak.kmm.tvshow;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tiktak.kmm.source.ScanDetails;

/**
 * Ergebnis eines Serien-Scans. {@code added} bis {@code unchanged} zählen Episoden, damit die
 * Oberfläche Film- und Serien-Scans gleich darstellen kann.
 *
 * @param showsAdded        neu gefundene Serien
 * @param showsRemoved      verschwundene Serien
 * @param nfoErrors         unlesbare NFOs
 * @param unrecognizedFiles Videodateien ohne erkennbare Staffel und Episode (gekürzt)
 * @param details           die betroffenen Serien und Episoden, gekürzt
 * @param newWithoutStreams IDs der neu gefundenen Episoden ohne technische Angaben in der NFO
 * @param showsWithNewEpisodes IDs der Serien mit neu gefundenen Episoden
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
