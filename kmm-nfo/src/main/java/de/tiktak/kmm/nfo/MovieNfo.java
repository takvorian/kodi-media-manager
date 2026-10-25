package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Die Felder einer Kodi-Film-NFO, die KMM verwaltet. Nicht gesetzte Werte sind {@code null}.
 *
 * @param runtime   Laufzeit in Minuten
 * @param uniqueIds IDs je Anbieter in Reihenfolge, Schlüssel klein geschrieben (z. B. {@code imdb}, {@code tmdb})
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
        List<String> genres,
        Map<String, String> uniqueIds) {

    public static final String IMDB = "imdb";
    public static final String TMDB = "tmdb";

    public MovieNfo {
        genres = genres == null ? List.of() : List.copyOf(genres);
        uniqueIds = uniqueIds == null ? Map.of() : lowerCaseKeysInOrder(uniqueIds);
    }

    /** Die Reihenfolge bleibt erhalten: Die erste ID wird beim Schreiben zur Standard-ID ({@code default}). */
    private static Map<String, String> lowerCaseKeysInOrder(Map<String, String> uniqueIds) {
        Map<String, String> normalized = new LinkedHashMap<>();
        uniqueIds.forEach((type, value) -> normalized.put(type.toLowerCase(Locale.ROOT), value));
        return Collections.unmodifiableMap(normalized);
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
