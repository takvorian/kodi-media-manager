package de.tiktak.kmm.tvshow;

import java.nio.file.Path;

/**
 * Die Videodatei einer Episode, wie ffprobe sie braucht. Episoden einer Mehrfachepisoden-Datei teilen
 * sich die Datei.
 *
 * @param hasStreamDetails ob technische Angaben bekannt sind
 */
public record EpisodeMedia(long episodeId, String showTitle, int season, int episode, Path file,
        boolean hasStreamDetails) {

    static EpisodeMedia of(Episode episode, String showTitle) {
        return new EpisodeMedia(episode.getId(), showTitle, episode.getSeason(), episode.getEpisode(), episode.file(),
                episode.hasStreamDetails());
    }
}
