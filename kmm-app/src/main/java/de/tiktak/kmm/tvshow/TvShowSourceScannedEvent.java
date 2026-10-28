package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Ein Scan hat die Serien einer Quelle mit dem Dateisystem abgeglichen.
 *
 * @param added             neu gefundene Serien und Episoden
 * @param newWithoutStreams IDs der neu gefundenen Episoden ohne technische Angaben in der NFO
 * @param showsWithNewEpisodes IDs der Serien mit neu gefundenen Episoden
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
