package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Content of a {@code tvshow.nfo}.
 *
 * @param premiered   first aired date
 * @param runtime     typical episode length in minutes
 * @param status      e.g. {@code Continuing} or {@code Ended}
 * @param uniqueIds   IDs by provider ({@code tvdb}, {@code tmdb}, {@code imdb}); the first one is the default
 * @param seasonNames season titles by season number ({@code <namedseason>})
 * @param userRating  own rating 1–10 or {@code null}
 * @param trailer     trailer link ({@code <trailer>}); {@link #NO_TRAILER} if the NFO has none, and {@code null} if
 *                    it is unknown – an existing entry then stays unchanged when writing
 * @param language    language ({@code <language>}, written by Ember) or {@code null}; the {@link LanguageNfoWriter}
 *                    writes it, otherwise an existing entry stays unchanged
 */
public record TvShowNfo(
        String title,
        String originalTitle,
        String sortTitle,
        LocalDate premiered,
        String plot,
        Integer runtime,
        String mpaa,
        String status,
        List<String> genres,
        List<String> studios,
        List<String> tags,
        Map<String, String> uniqueIds,
        List<NfoRating> ratings,
        List<NfoActor> actors,
        SortedMap<Integer, String> seasonNames,
        Integer userRating,
        String trailer,
        String language) {

    public static final String TVDB = "tvdb";
    /** No trailer; writing removes an existing entry. */
    public static final String NO_TRAILER = MovieNfoSupplement.NO_TRAILER;

    public TvShowNfo {
        genres = NfoCollections.list(genres);
        studios = NfoCollections.list(studios);
        tags = NfoCollections.list(tags);
        uniqueIds = NfoCollections.orderedIds(uniqueIds);
        ratings = NfoCollections.list(ratings);
        actors = NfoCollections.list(actors);
        seasonNames = NfoCollections.sorted(seasonNames);
        trailer = trailer == null ? null : trailer.strip();
    }

    /** Details without a language. */
    public TvShowNfo(String title, String originalTitle, String sortTitle, LocalDate premiered, String plot,
            Integer runtime, String mpaa, String status, List<String> genres, List<String> studios, List<String> tags,
            Map<String, String> uniqueIds, List<NfoRating> ratings, List<NfoActor> actors,
            SortedMap<Integer, String> seasonNames, Integer userRating, String trailer) {
        this(title, originalTitle, sortTitle, premiered, plot, runtime, mpaa, status, genres, studios, tags, uniqueIds,
                ratings, actors, seasonNames, userRating, trailer, null);
    }

    /** Details with an unknown trailer; an existing entry stays unchanged when writing. */
    public TvShowNfo(String title, String originalTitle, String sortTitle, LocalDate premiered, String plot,
            Integer runtime, String mpaa, String status, List<String> genres, List<String> studios, List<String> tags,
            Map<String, String> uniqueIds, List<NfoRating> ratings, List<NfoActor> actors,
            SortedMap<Integer, String> seasonNames, Integer userRating) {
        this(title, originalTitle, sortTitle, premiered, plot, runtime, mpaa, status, genres, studios, tags, uniqueIds,
                ratings, actors, seasonNames, userRating, null);
    }

    /** Copy with a different trailer link; {@link #NO_TRAILER} or {@code null} as for the constructor. */
    public TvShowNfo withTrailer(String newTrailer) {
        return new TvShowNfo(title, originalTitle, sortTitle, premiered, plot, runtime, mpaa, status, genres, studios,
                tags, uniqueIds, ratings, actors, seasonNames, userRating, newTrailer, language);
    }
}
