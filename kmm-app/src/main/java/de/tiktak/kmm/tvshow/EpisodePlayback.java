package de.tiktak.kmm.tvshow;

import java.time.Instant;

/**
 * Playback state and own rating of an episode.
 *
 * @param showTitle    title of the TV show
 * @param playCount    number of plays or {@code null}
 * @param lastPlayedAt last play or {@code null}
 * @param userRating   own rating 1-10 or {@code null}
 */
public record EpisodePlayback(long episodeId, String showTitle, String filePath, int season, int episode,
                              Integer playCount, Instant lastPlayedAt, Integer userRating) {

    static EpisodePlayback of(Episode episode, String showTitle) {
        return new EpisodePlayback(episode.getId(), showTitle, episode.getFilePath(), episode.getSeason(),
                episode.getEpisode(), episode.getPlayCount(), episode.getLastPlayedAt(), episode.getUserRating());
    }
}
