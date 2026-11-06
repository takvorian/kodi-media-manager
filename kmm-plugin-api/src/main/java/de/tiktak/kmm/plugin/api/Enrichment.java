package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Additions of a provider to a movie or TV show: ratings and details KMM uses to fill empty fields. Unknown details
 * are {@code null} or empty.
 *
 * @param ratings       ratings of the provider
 * @param plot          plot
 * @param runtime       runtime in minutes
 * @param premiered     premiere or first air date
 * @param certification age rating as the provider states it
 * @param actors        names of the actors without roles
 */
public record Enrichment(
        List<ProviderRating> ratings,
        String plot,
        Integer runtime,
        LocalDate premiered,
        String certification,
        List<String> genres,
        List<String> directors,
        List<String> writers,
        List<String> actors,
        List<String> countries) {

    public Enrichment {
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
        genres = genres == null ? List.of() : List.copyOf(genres);
        directors = directors == null ? List.of() : List.copyOf(directors);
        writers = writers == null ? List.of() : List.copyOf(writers);
        actors = actors == null ? List.of() : List.copyOf(actors);
        countries = countries == null ? List.of() : List.copyOf(countries);
    }

    /** Ratings only. */
    public static Enrichment ofRatings(List<ProviderRating> ratings) {
        return new Enrichment(ratings, null, null, null, null, null, null, null, null, null);
    }
}
