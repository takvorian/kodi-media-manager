package de.tiktak.kmm.tvshow;

import java.nio.file.Path;

/**
 * The video file of an episode, as ffprobe needs it. Episodes of a multi-episode file share
 * the file.
 *
 * @param hasStreamDetails whether technical details are known
 */
public record EpisodeMedia(long episodeId, String showTitle, int season, int episode, Path file,
        boolean hasStreamDetails) {

    static EpisodeMedia of(Episode episode, String showTitle) {
        return new EpisodeMedia(episode.getId(), showTitle, episode.getSeason(), episode.getEpisode(), episode.file(),
                episode.hasStreamDetails());
    }
}
