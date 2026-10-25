package de.tiktak.kmm.nfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Weitere Angaben einer Film-NFO neben den Kernmetadaten: Mitwirkende, Zuordnungen, Bewertungen
 * und Nutzerdaten. Nicht gesetzte Einzelwerte sind {@code null}, fehlende Listen leer.
 *
 * @param writers    Drehbuchautoren ({@code <credits>})
 * @param set        Filmset oder {@code null}
 * @param userRating eigene Bewertung (0–10) oder {@code null}
 * @param playCount  Anzahl der Wiedergaben oder {@code null}
 * @param lastPlayed letzte Wiedergabe (lokale Zeit) oder {@code null}
 * @param dateAdded  Aufnahme in die Bibliothek (lokale Zeit) oder {@code null}
 */
public record MovieNfoSupplement(
        List<NfoActor> actors,
        List<String> directors,
        List<String> writers,
        List<String> studios,
        List<String> countries,
        List<String> tags,
        List<NfoRating> ratings,
        NfoMovieSet set,
        Integer userRating,
        Integer playCount,
        LocalDateTime lastPlayed,
        LocalDateTime dateAdded) {

    public static final MovieNfoSupplement EMPTY = new MovieNfoSupplement(List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), null, null, null, null, null);

    public MovieNfoSupplement {
        actors = copy(actors);
        directors = copy(directors);
        writers = copy(writers);
        studios = copy(studios);
        countries = copy(countries);
        tags = copy(tags);
        ratings = copy(ratings);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
