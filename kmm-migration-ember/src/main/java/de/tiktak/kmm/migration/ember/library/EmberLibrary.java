package de.tiktak.kmm.migration.ember.library;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Der Filmbestand einer Ember-Datenbank.
 *
 * @param excludedDirectories Ordner, die Ember beim Scan überspringt (Windows-Pfade)
 */
public record EmberLibrary(
        List<EmberMovieSource> sources,
        List<EmberMovie> movies,
        List<EmberMovieSet> sets,
        List<String> excludedDirectories) {

    public EmberLibrary {
        sources = List.copyOf(sources);
        movies = List.copyOf(movies);
        sets = List.copyOf(sets);
        excludedDirectories = List.copyOf(excludedDirectories);
    }

    public Map<Long, EmberMovieSet> setsById() {
        return sets.stream().collect(Collectors.toMap(EmberMovieSet::id, Function.identity()));
    }
}
