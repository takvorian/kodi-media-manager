package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Die Felder einer Kodi-Film-NFO, die KMM verwaltet. Nicht gesetzte Werte sind {@code null}.
 *
 * @param runtime   Laufzeit in Minuten
 * @param edition   Fassung, z. B. „Director's Cut“ (wie Ember)
 * @param videoSource Videoquelle, z. B. {@code bluray} (wie Ember)
 * @param uniqueIds IDs je Anbieter in Reihenfolge, Schlüssel klein geschrieben (z. B. {@code imdb}, {@code tmdb})
 * @param supplement weitere Angaben; {@code null} heißt „unbekannt“: Beim Schreiben bleiben die
 *                   entsprechenden Elemente einer vorhandenen NFO dann unverändert
 * @param language   Sprache ({@code <language>}, von Ember geschrieben) oder {@code null}; Schreiben übernimmt der
 *                   {@link LanguageNfoWriter}, sonst bleibt ein vorhandener Eintrag unverändert
 */
public record MovieNfo(
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
        String edition,
        String videoSource,
        List<String> genres,
        Map<String, String> uniqueIds,
        MovieNfoSupplement supplement,
        String language) {

    public static final String IMDB = "imdb";
    public static final String TMDB = "tmdb";

    public MovieNfo {
        genres = NfoCollections.list(genres);
        uniqueIds = NfoCollections.orderedIds(uniqueIds);
    }

    /** NFO-Inhalt ohne Angabe der Sprache. */
    public MovieNfo(String title, String originalTitle, String sortTitle, Integer year, LocalDate premiered,
            String plot, String outline, String tagline, Integer runtime, String mpaa, String edition,
            String videoSource, List<String> genres, Map<String, String> uniqueIds, MovieNfoSupplement supplement) {
        this(title, originalTitle, sortTitle, year, premiered, plot, outline, tagline, runtime, mpaa, edition,
                videoSource, genres, uniqueIds, supplement, null);
    }

    /** NFO-Inhalt ohne bekannte weitere Angaben. */
    public MovieNfo(String title, String originalTitle, String sortTitle, Integer year, LocalDate premiered,
            String plot, String outline, String tagline, Integer runtime, String mpaa, String edition,
            String videoSource, List<String> genres, Map<String, String> uniqueIds) {
        this(title, originalTitle, sortTitle, year, premiered, plot, outline, tagline, runtime, mpaa, edition,
                videoSource, genres, uniqueIds, null);
    }

    public Optional<String> uniqueId(String type) {
        return Optional.ofNullable(uniqueIds.get(type.toLowerCase(Locale.ROOT)));
    }

    /**
     * Erscheinungsjahr, notfalls aus dem Datum der Erstaufführung abgeleitet (Kodi 19+ schreibt
     * nur noch {@code premiered}).
     */
    public Integer effectiveYear() {
        if (year != null) {
            return year;
        }
        return premiered == null ? null : premiered.getYear();
    }
}
