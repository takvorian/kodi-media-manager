package de.tiktak.kmm.tvshow;

import java.time.Instant;

/**
 * Wiedergabestand einer Episode.
 *
 * @param showTitle    Titel der Serie
 * @param playCount    Anzahl der Wiedergaben oder {@code null}
 * @param lastPlayedAt letzte Wiedergabe oder {@code null}
 */
public record EpisodePlayback(long episodeId, String showTitle, String filePath, int season, int episode,
                              Integer playCount, Instant lastPlayedAt) {

    static EpisodePlayback of(Episode episode, String showTitle) {
        return new EpisodePlayback(episode.getId(), showTitle, episode.getFilePath(), episode.getSeason(),
                episode.getEpisode(), episode.getPlayCount(), episode.getLastPlayedAt());
    }
}
