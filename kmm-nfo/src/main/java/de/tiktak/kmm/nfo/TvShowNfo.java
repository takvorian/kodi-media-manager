package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Inhalt einer {@code tvshow.nfo}.
 *
 * @param premiered   Erstausstrahlung
 * @param runtime     typische Episodenlänge in Minuten
 * @param status      z. B. {@code Continuing} oder {@code Ended}
 * @param uniqueIds   IDs nach Anbieter ({@code tvdb}, {@code tmdb}, {@code imdb}); die erste gilt als Standard
 * @param seasonNames Staffeltitel nach Staffelnummer ({@code <namedseason>})
 * @param userRating  eigene Bewertung 1–10 oder {@code null}
 * @param trailer     Trailer-Link ({@code <trailer>}); {@link #NO_TRAILER}, wenn die NFO keinen hat, und {@code null},
 *                    wenn er unbekannt ist – dann bleibt ein vorhandener Eintrag beim Schreiben unverändert
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
        String trailer) {

    public static final String TVDB = "tvdb";
    /** Kein Trailer; beim Schreiben wird ein vorhandener Eintrag entfernt. */
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

    /** Angaben mit unbekanntem Trailer; ein vorhandener Eintrag bleibt beim Schreiben unverändert. */
    public TvShowNfo(String title, String originalTitle, String sortTitle, LocalDate premiered, String plot,
            Integer runtime, String mpaa, String status, List<String> genres, List<String> studios, List<String> tags,
            Map<String, String> uniqueIds, List<NfoRating> ratings, List<NfoActor> actors,
            SortedMap<Integer, String> seasonNames, Integer userRating) {
        this(title, originalTitle, sortTitle, premiered, plot, runtime, mpaa, status, genres, studios, tags, uniqueIds,
                ratings, actors, seasonNames, userRating, null);
    }

    /** Kopie mit anderem Trailer-Link; {@link #NO_TRAILER} oder {@code null} wie beim Konstruktor. */
    public TvShowNfo withTrailer(String newTrailer) {
        return new TvShowNfo(title, originalTitle, sortTitle, premiered, plot, runtime, mpaa, status, genres, studios,
                tags, uniqueIds, ratings, actors, seasonNames, userRating, newTrailer);
    }
}
