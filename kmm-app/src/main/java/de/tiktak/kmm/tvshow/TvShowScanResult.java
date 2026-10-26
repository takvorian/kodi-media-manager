package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Ergebnis eines Serien-Scans. {@code added} bis {@code unchanged} zählen Episoden, damit die
 * Oberfläche Film- und Serien-Scans gleich darstellen kann.
 *
 * @param showsAdded        neu gefundene Serien
 * @param showsRemoved      verschwundene Serien
 * @param nfoErrors         unlesbare NFOs
 * @param unrecognizedFiles Videodateien ohne erkennbare Staffel und Episode (gekürzt)
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
        List<String> unrecognizedFiles) {

    public TvShowScanResult {
        unrecognizedFiles = List.copyOf(unrecognizedFiles);
    }
}
