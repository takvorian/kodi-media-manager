package de.tiktak.kmm.migration.ember.library;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * Ein Film aus der Ember-Datenbank mit allen verknüpften Angaben. Leere Texte sind {@code null},
 * fehlende Listen leer.
 *
 * @param path       Pfad der Filmdatei, wie Ember ihn gespeichert hat (Windows)
 * @param mpaa       Altersfreigabe, z. B. {@code Germany:16}
 * @param uniqueIds  IDs je Anbieter, Schlüssel klein geschrieben (z. B. {@code imdb}, {@code tmdb}, {@code tmdbcol})
 * @param sets       Zugehörigkeit zu Filmsets
 * @param streams    Video-, Ton- und Untertitelspuren
 * @param userRating eigene Bewertung oder {@code null}, wenn keine vergeben ist
 */
@Builder
public record EmberMovie(
        long id,
        long sourceId,
        String path,
        String title,
        String originalTitle,
        String sortTitle,
        Integer year,
        LocalDate premiered,
        String plot,
        String outline,
        String tagline,
        Integer runtime,
        String mpaa,
        Map<String, String> uniqueIds,
        List<String> genres,
        List<EmberActor> actors,
        List<String> directors,
        List<String> writers,
        List<String> studios,
        List<String> countries,
        List<String> tags,
        List<EmberRating> ratings,
        List<EmberSetLink> sets,
        EmberStreams streams,
        LocalDateTime dateAdded,
        Integer playCount,
        LocalDateTime lastPlayed,
        Integer userRating) {

    public EmberMovie {
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
        genres = copy(genres);
        actors = copy(actors);
        directors = copy(directors);
        writers = copy(writers);
        studios = copy(studios);
        countries = copy(countries);
        tags = copy(tags);
        ratings = copy(ratings);
        sets = copy(sets);
        streams = streams == null ? EmberStreams.NONE : streams;
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * Ein Darsteller in der Reihenfolge aus Ember.
     *
     * @param thumb URL des Bildes oder {@code null}
     */
    public record EmberActor(String name, String role, String thumb) {
    }

    /**
     * Eine Bewertung.
     *
     * @param source z. B. {@code imdb}, {@code themoviedb}, {@code trakt}
     */
    public record EmberRating(String source, double value, Integer max, Integer votes, boolean defaultRating) {
    }

    /**
     * Zugehörigkeit zu einem Filmset.
     *
     * @param order Position im Set oder {@code null}, wenn Ember keine kennt
     */
    public record EmberSetLink(long setId, Integer order) {
    }
}
