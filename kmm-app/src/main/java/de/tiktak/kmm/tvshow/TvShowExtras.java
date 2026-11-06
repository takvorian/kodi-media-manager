package de.tiktak.kmm.tvshow;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Further details of a TV show that KMM takes over from the NFO and migration but does not edit yet.
 *
 * @param seasonNames season titles by season number
 */
public record TvShowExtras(List<TvActor> actors, List<TvRating> ratings, SortedMap<Integer, String> seasonNames) {

    public static final TvShowExtras EMPTY = new TvShowExtras(List.of(), List.of(), new TreeMap<>());

    public TvShowExtras {
        actors = actors == null ? List.of() : actors.stream()
                .filter(actor -> actor.name() != null)
                .limit(TvTexts.MAX_ACTORS)
                .toList();
        ratings = ratings == null ? List.of() : ratings.stream()
                .filter(rating -> rating.source() != null)
                .toList();
        SortedMap<Integer, String> names = new TreeMap<>();
        if (seasonNames != null) {
            seasonNames.forEach((number, name) -> {
                String cleaned = TvTexts.clean(name, TvTexts.MAX_TITLE_LENGTH);
                if (number != null && number >= 0 && cleaned != null) {
                    names.put(number, cleaned);
                }
            });
        }
        seasonNames = Collections.unmodifiableSortedMap(names);
    }

    /** The same details with different ratings. */
    public TvShowExtras withRatings(List<TvRating> newRatings) {
        return new TvShowExtras(actors, newRatings, seasonNames);
    }

    /** The same details with different actors. */
    public TvShowExtras withActors(List<TvActor> newActors) {
        return new TvShowExtras(newActors, ratings, seasonNames);
    }
}
