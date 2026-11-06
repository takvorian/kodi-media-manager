package de.tiktak.kmm.migration.ember.library;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import lombok.Builder;

/**
 * A TV show from the Ember database with all linked data. Empty texts are {@code null}, missing lists are
 * empty.
 *
 * @param path         directory of the TV show as Ember stored it (Windows)
 * @param uniqueIds    IDs per provider, keys in lower case (e.g. {@code tvdb}, {@code tmdb}, {@code imdb})
 * @param status       e.g. {@code Continuing} or {@code Ended}
 * @param seasonTitles season titles by season number
 * @param userRating   user rating, or {@code null} if none is given
 * @param language     language of the TV show, e.g. {@code de-DE}, or {@code null}
 */
@Builder
public record EmberTvShow(
        long id,
        long sourceId,
        String path,
        String title,
        String originalTitle,
        String sortTitle,
        LocalDate premiered,
        String plot,
        Integer runtime,
        String mpaa,
        String status,
        Map<String, String> uniqueIds,
        List<String> genres,
        List<String> studios,
        List<String> tags,
        List<EmberActor> actors,
        List<EmberRating> ratings,
        SortedMap<Integer, String> seasonTitles,
        Integer userRating,
        String language) {

    public EmberTvShow {
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
        genres = copy(genres);
        studios = copy(studios);
        tags = copy(tags);
        actors = copy(actors);
        ratings = copy(ratings);
        seasonTitles = seasonTitles == null ? new TreeMap<>() : new TreeMap<>(seasonTitles);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
