package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Episodes were removed from the library, e.g. one episode, all episodes of a multi-episode file or of a season.
 * Carries what Kodi needs to find them, because the entries are gone.
 *
 * @param showTitle title of the TV show, for messages
 */
public record EpisodesRemovedEvent(String showTitle, List<RemovedEpisode> episodes) {

    public EpisodesRemovedEvent {
        episodes = List.copyOf(episodes);
    }

    /** An episode by its file and, for multi-episode files, by season and number. */
    public record RemovedEpisode(String filePath, int season, int episode) {
    }
}
