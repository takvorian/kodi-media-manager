package de.tiktak.kmm.migration.ember;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tables of the Ember database (schema v48) that KMM reads.
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
