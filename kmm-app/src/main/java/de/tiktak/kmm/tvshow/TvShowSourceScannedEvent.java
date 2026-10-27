package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Ein Scan hat die Serien einer Quelle mit dem Dateisystem abgeglichen.
 *
 * @param added             neu gefundene Serien und Episoden
 * @param newWithoutStreams IDs der neu gefundenen Episoden ohne technische Angaben in der NFO
 */
public record TvShowSourceScannedEvent(long sourceId, int added, List<Long> newWithoutStreams) {

    public TvShowSourceScannedEvent {
        newWithoutStreams = List.copyOf(newWithoutStreams);
    }

    public TvShowSourceScannedEvent(long sourceId, int added) {
        this(sourceId, added, List.of());
    }
}
