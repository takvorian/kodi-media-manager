package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

/**
 * Ergebnis eines Serien-Scrapers: die Serie mit allen Staffeln und Episoden, die der Anbieter kennt.
 * Felder, die der Scraper nicht kennt, sind {@code null} bzw. leer.
 *
 * @param premiered     Erstausstrahlung
 * @param runtime       übliche Episodenlänge in Minuten
 * @param certification Altersfreigabe in der Schreibweise für die NFO, z. B. {@code FSK 12}
 * @param status        z. B. {@code Continuing} oder {@code Ended}
 * @param uniqueIds     IDs je Anbieter, z. B. {@code tvdb -> 73739}, {@code imdb -> tt0411008}
 */
@Builder
public record TvShowResult(
        String title,
        String originalTitle,
        Integer year,
        LocalDate premiered,
        String plot,
        Integer runtime,
        String certification,
        String status,
        List<String> genres,
        List<String> studios,
        List<CastMember> actors,
        List<ProviderRating> ratings,
        Map<String, String> uniqueIds,
        List<SeasonResult> seasons,
        List<EpisodeResult> episodes) {

    public TvShowResult {
        Objects.requireNonNull(title, "title must not be null");
        genres = genres == null ? List.of() : List.copyOf(genres);
        studios = studios == null ? List.of() : List.copyOf(studios);
        actors = actors == null ? List.of() : List.copyOf(actors);
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
    }
}
