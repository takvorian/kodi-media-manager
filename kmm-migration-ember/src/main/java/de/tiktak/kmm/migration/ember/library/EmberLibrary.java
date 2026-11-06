package de.tiktak.kmm.migration.ember.library;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The movies of an Ember database.
 *
 * @param excludedDirectories folders Ember skips when scanning (Windows paths)
 * @param movieLists          custom movie lists from Ember's filter editor
 */
public record EmberLibrary(
        List<EmberMovieSource> sources,
        List<EmberMovie> movies,
        List<EmberMovieSet> sets,
        List<String> excludedDirectories,
        List<EmberMediaList> movieLists) {

    public EmberLibrary {
        sources = List.copyOf(sources);
        movies = List.copyOf(movies);
        sets = List.copyOf(sets);
        excludedDirectories = List.copyOf(excludedDirectories);
        movieLists = movieLists == null ? List.of() : List.copyOf(movieLists);
    }

    /** A library without custom movie lists. */
    public EmberLibrary(List<EmberMovieSource> sources, List<EmberMovie> movies, List<EmberMovieSet> sets,
            List<String> excludedDirectories) {
        this(sources, movies, sets, excludedDirectories, null);
    }

    public Map<Long, EmberMovieSet> setsById() {
        return sets.stream().collect(Collectors.toMap(EmberMovieSet::id, Function.identity()));
    }
}
