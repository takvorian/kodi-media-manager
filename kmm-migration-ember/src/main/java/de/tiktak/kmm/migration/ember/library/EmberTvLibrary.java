package de.tiktak.kmm.migration.ember.library;

import java.util.List;

/**
 * Der Serienbestand einer Ember-Datenbank.
 *
 * @param episodes             Episoden mit Datei
 * @param episodesWithoutFile  Episoden, die Ember nur aus der Online-Suche kennt (fehlende Episoden)
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
