package de.tiktak.kmm.tvshow;

import java.time.LocalDate;

/**
 * Episode in der Staffelübersicht einer Serie.
 *
 * @param playCount Anzahl der Wiedergaben oder {@code null}
 */
public record EpisodeSummary(
        long id,
        int season,
        int episode,
        String title,
        LocalDate aired,
        String filePath,
        Integer playCount) {

    static EpisodeSummary from(Episode episode) {
        return new EpisodeSummary(episode.getId(), episode.getSeason(), episode.getEpisode(), episode.getTitle(),
                episode.getAired(), episode.getFilePath(), episode.getPlayCount());
    }
}
