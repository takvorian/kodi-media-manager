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
 * Eine Serie aus der Ember-Datenbank mit allen verknüpften Angaben. Leere Texte sind {@code null},
 * fehlende Listen leer.
 *
 * @param path         Verzeichnis der Serie, wie Ember es gespeichert hat (Windows)
 * @param uniqueIds    IDs je Anbieter, Schlüssel klein geschrieben (z. B. {@code tvdb}, {@code tmdb}, {@code imdb})
 * @param status       z. B. {@code Continuing} oder {@code Ended}
 * @param seasonTitles Staffeltitel nach Staffelnummer
 * @param userRating   eigene Bewertung oder {@code null}, wenn keine vergeben ist
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
        Integer userRating) {

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
