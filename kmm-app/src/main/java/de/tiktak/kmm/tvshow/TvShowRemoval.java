package de.tiktak.kmm.tvshow;

import java.util.List;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.tvshow.EpisodesRemovedEvent.RemovedEpisode;

/**
 * What a removal of episodes or of a whole TV show comprises.
 *
 * @param subject    what is removed, for the job title: the TV show title, for episodes with their numbers
 * @param episodeIds the episodes to remove; for a whole TV show all of them
 * @param episodes   the same episodes as Kodi finds them
 * @param seasons    seasons that lose episodes; their details are dropped once they have none left
 * @param wholeShow  whether the TV show itself is removed
 * @param entries    the entries for the preview
 */
record TvShowRemoval(long showId, String showTitle, String directory, String subject, List<Long> episodeIds,
                     List<RemovedEpisode> episodes, Set<Integer> seasons, boolean wholeShow, RemovalFiles files,
                     List<LocalizedText> entries) {

    TvShowRemoval {
        episodeIds = List.copyOf(episodeIds);
        episodes = List.copyOf(episodes);
        seasons = Set.copyOf(seasons);
        entries = List.copyOf(entries);
    }

    RemovalPlan plan() {
        return files.plan(entries);
    }
}
