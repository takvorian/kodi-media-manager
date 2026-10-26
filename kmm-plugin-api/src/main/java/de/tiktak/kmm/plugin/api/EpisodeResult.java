package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.Builder;

/**
 * Eine Episode beim Anbieter in der eingestellten Episodenreihenfolge.
 *
 * @param runtime    Laufzeit in Minuten oder {@code null}
 * @param guestStars Gaststars der Episode
 * @param uniqueIds  IDs der Episode je Anbieter, z. B. {@code tvdb -> 127131}
 * @param thumb      Standbild der Episode oder {@code null}
 */
@Builder
public record EpisodeResult(
        int season,
        int episode,
        String title,
        String plot,
        LocalDate aired,
        Integer runtime,
        List<String> directors,
        List<String> writers,
        List<CastMember> guestStars,
        List<ProviderRating> ratings,
        Map<String, String> uniqueIds,
        URI thumb) {

    public EpisodeResult {
        directors = directors == null ? List.of() : List.copyOf(directors);
        writers = writers == null ? List.of() : List.copyOf(writers);
        guestStars = guestStars == null ? List.of() : List.copyOf(guestStars);
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
    }
}
