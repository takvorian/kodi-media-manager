package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Ein Scan hat die Filme einer Quelle mit dem Dateisystem abgeglichen.
 *
 * @param added             neu gefundene Filme
 * @param removed           verschwundene Filme
 * @param newWithoutStreams IDs der neu gefundenen Filme ohne technische Angaben in der NFO
 */
public record MovieSourceScannedEvent(long sourceId, int added, int removed, List<Long> newWithoutStreams) {

    public MovieSourceScannedEvent {
        newWithoutStreams = List.copyOf(newWithoutStreams);
    }

    public MovieSourceScannedEvent(long sourceId, int added, int removed) {
        this(sourceId, added, removed, List.of());
    }
}
