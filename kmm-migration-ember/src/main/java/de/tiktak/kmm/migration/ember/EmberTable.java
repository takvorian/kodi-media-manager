package de.tiktak.kmm.migration.ember;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tabellen der Ember-Datenbank (Schema v48), die KMM liest.
 */
@Getter
@RequiredArgsConstructor
enum EmberTable {
    MOVIE("movie"),
    MOVIE_SET("sets"),
    TV_SHOW("tvshow"),
    EPISODE("episode"),
    MOVIE_SOURCE("moviesource"),
    TV_SHOW_SOURCE("tvshowsource");

    private final String tableName;
}
