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
 * @param streams    Video-, Ton- und Untertitelspuren; leer, wenn unbekannt
 * @param trailer    Trailer-Link ({@code <trailer>}); {@link #NO_TRAILER}, wenn die NFO keinen hat, und {@code null},
 *                   wenn er unbekannt ist – dann bleibt ein vorhandener Eintrag beim Schreiben unverändert
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
        LocalDateTime dateAdded,
        StreamDetails streams,
        String trailer) {

    /** Kein Trailer; beim Schreiben wird ein vorhandener Eintrag entfernt. */
    public static final String NO_TRAILER = "";

    public static final MovieNfoSupplement EMPTY = new MovieNfoSupplement(List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), null, null, null, null, null, StreamDetails.EMPTY);

    public MovieNfoSupplement {
        actors = copy(actors);
        directors = copy(directors);
        writers = copy(writers);
        studios = copy(studios);
        countries = copy(countries);
        tags = copy(tags);
        ratings = copy(ratings);
        streams = streams == null ? StreamDetails.EMPTY : streams;
        trailer = trailer == null ? null : trailer.strip();
    }

    /** Kopie mit anderem Trailer-Link; {@link #NO_TRAILER} oder {@code null} wie beim Konstruktor. */
    public MovieNfoSupplement withTrailer(String newTrailer) {
        return new MovieNfoSupplement(actors, directors, writers, studios, countries, tags, ratings, set, userRating,
                playCount, lastPlayed, dateAdded, streams, newTrailer);
    }

    /** Angaben mit unbekanntem Trailer; ein vorhandener Eintrag bleibt beim Schreiben unverändert. */
    public MovieNfoSupplement(List<NfoActor> actors, List<String> directors, List<String> writers,
            List<String> studios, List<String> countries, List<String> tags, List<NfoRating> ratings, NfoMovieSet set,
            Integer userRating, Integer playCount, LocalDateTime lastPlayed, LocalDateTime dateAdded,
            StreamDetails streams) {
        this(actors, directors, writers, studios, countries, tags, ratings, set, userRating, playCount, lastPlayed,
                dateAdded, streams, null);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
