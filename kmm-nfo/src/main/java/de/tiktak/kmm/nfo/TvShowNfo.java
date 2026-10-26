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
        Integer userRating) {

    public static final String TVDB = "tvdb";

    public TvShowNfo {
        genres = NfoCollections.list(genres);
        studios = NfoCollections.list(studios);
        tags = NfoCollections.list(tags);
        uniqueIds = NfoCollections.orderedIds(uniqueIds);
        ratings = NfoCollections.list(ratings);
        actors = NfoCollections.list(actors);
        seasonNames = NfoCollections.sorted(seasonNames);
    }
}
