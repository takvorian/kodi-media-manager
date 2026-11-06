package de.tiktak.kmm.plugin.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Builder;

/**
 * Result of a TV show scraper: the TV show with all seasons and episodes the provider knows. Fields the scraper does
 * not know are {@code null} or empty.
 *
 * @param premiered     first air date
 * @param runtime       usual episode length in minutes
 * @param certification age rating as written in the NFO, e.g. {@code FSK 12}
 * @param status        e.g. {@code Continuing} or {@code Ended}
 * @param uniqueIds     IDs per provider, e.g. {@code tvdb -> 73739}, {@code imdb -> tt0411008}
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
