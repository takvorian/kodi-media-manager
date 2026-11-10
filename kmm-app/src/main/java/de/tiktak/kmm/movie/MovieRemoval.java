package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.removal.RemovalPlan;

/**
 * What removing one or more movies comprises.
 *
 * @param movies  the movies with what Kodi needs to find them
 * @param entries the entries for the preview
 */
record MovieRemoval(List<RemovedMovie> movies, RemovalFiles files, List<LocalizedText> entries) {

    MovieRemoval {
        movies = List.copyOf(movies);
        entries = List.copyOf(entries);
    }

    RemovalPlan plan() {
        return files.plan(entries);
    }

    List<Long> movieIds() {
        return movies.stream().map(RemovedMovie::id).toList();
    }

    record RemovedMovie(long id, String title, String filePath) {
    }
}
