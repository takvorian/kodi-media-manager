package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fields of a Kodi movie NFO that KMM manages. Values that are not set are {@code null}.
 *
 * @param runtime   runtime in minutes
 * @param edition   edition, e.g. "Director's Cut" (like Ember)
 * @param videoSource video source, e.g. {@code bluray} (like Ember)
 * @param uniqueIds IDs per provider in order, keys in lower case (e.g. {@code imdb}, {@code tmdb})
 * @param supplement further details; {@code null} means "unknown": when writing, the corresponding elements of an
 *                   existing NFO then stay unchanged
 * @param language   language ({@code <language>}, written by Ember) or {@code null}; the {@link LanguageNfoWriter}
 *                   writes it, otherwise an existing entry stays unchanged
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

    /** NFO content without a language. */
    public MovieNfo(String title, String originalTitle, String sortTitle, Integer year, LocalDate premiered,
            String plot, String outline, String tagline, Integer runtime, String mpaa, String edition,
            String videoSource, List<String> genres, Map<String, String> uniqueIds, MovieNfoSupplement supplement) {
        this(title, originalTitle, sortTitle, year, premiered, plot, outline, tagline, runtime, mpaa, edition,
                videoSource, genres, uniqueIds, supplement, null);
    }

    /** NFO content without known further details. */
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
     * Release year, derived from the premiere date if necessary (Kodi 19+ only writes {@code premiered}).
     */
    public Integer effectiveYear() {
        if (year != null) {
            return year;
        }
        return premiered == null ? null : premiered.getYear();
    }
}
