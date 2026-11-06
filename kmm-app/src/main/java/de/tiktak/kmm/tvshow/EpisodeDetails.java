package de.tiktak.kmm.tvshow;

import java.time.Instant;

/**
 * All details of an episode for the detail view.
 *
 * @param showTitle title of the TV show
 */
public record EpisodeDetails(
        long id,
        long tvShowId,
        String showTitle,
        String filePath,
        int season,
        int episode,
        Instant addedAt,
        EpisodeMetadata metadata,
        EpisodeExtras extras,
        TvNfoInfo nfo) {

    static EpisodeDetails from(Episode episode, TvShow show, TvNfoInfo nfo) {
        return new EpisodeDetails(episode.getId(), show.getId(), show.getTitle(), episode.getFilePath(),
                episode.getSeason(), episode.getEpisode(), episode.getAddedAt(), episode.metadata(), episode.extras(),
                nfo);
    }
}
