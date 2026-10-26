package de.tiktak.kmm.tvshow;

import java.time.Instant;

/**
 * Serie in der Listenansicht.
 *
 * @param episodeCount Anzahl der Episoden mit Datei
 */
public record TvShowSummary(
        long id,
        long sourceId,
        String title,
        Integer year,
        String directoryPath,
        long episodeCount,
        Instant addedAt) {

    static TvShowSummary from(TvShow show, long episodeCount) {
        return new TvShowSummary(show.getId(), show.getSourceId(), show.getTitle(), show.getReleaseYear(),
                show.getDirectoryPath(), episodeCount, show.getAddedAt());
    }
}
