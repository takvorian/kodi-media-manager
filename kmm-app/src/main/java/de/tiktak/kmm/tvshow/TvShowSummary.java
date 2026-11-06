package de.tiktak.kmm.tvshow;

import java.time.Instant;

/**
 * TV show in the list view.
 *
 * @param episodeCount number of episodes with a file
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
