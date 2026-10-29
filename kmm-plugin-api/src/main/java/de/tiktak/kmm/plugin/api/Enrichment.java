package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Ergänzungen eines Anbieters zu einem Film oder einer Serie: Bewertungen und Angaben, mit denen KMM leere Felder
 * füllt. Unbekannte Angaben sind {@code null} bzw. leer.
 *
 * @param ratings       Bewertungen des Anbieters
 * @param plot          Handlung
 * @param runtime       Laufzeit in Minuten
 * @param premiered     Erstaufführung bzw. Erstausstrahlung
 * @param certification Altersfreigabe, wie der Anbieter sie angibt
 * @param actors        Namen der Darsteller ohne Rollen
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

    /** Nur Bewertungen. */
    public static Enrichment ofRatings(List<ProviderRating> ratings) {
        return new Enrichment(ratings, null, null, null, null, null, null, null, null, null);
    }
}
