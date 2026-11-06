package de.tiktak.kmm.migration.ember.library;

import java.util.List;

/**
 * The TV shows of an Ember database.
 *
 * @param episodes             episodes with a file
 * @param episodesWithoutFile  episodes Ember only knows from the online search (missing episodes)
 */
public record EmberTvLibrary(
        List<EmberTvShowSource> sources,
        List<EmberTvShow> shows,
        List<EmberEpisode> episodes,
        int episodesWithoutFile) {

    public EmberTvLibrary {
        sources = List.copyOf(sources);
        shows = List.copyOf(shows);
        episodes = List.copyOf(episodes);
    }
}
