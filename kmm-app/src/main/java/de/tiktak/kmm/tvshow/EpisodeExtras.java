package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.List;

/**
 * Weitere Angaben zu einer Episode, die KMM aus NFO und Migration übernimmt, aber noch nicht bearbeitet.
 *
 * @param actors     Darsteller einschließlich Gaststars
 * @param playCount  Anzahl der Wiedergaben oder {@code null}
 * @param lastPlayed letzte Wiedergabe oder {@code null}
 */
public record EpisodeExtras(
        List<String> directors,
        List<String> writers,
        List<TvActor> actors,
        List<TvRating> ratings,
        Integer playCount,
        Instant lastPlayed) {

    public static final EpisodeExtras EMPTY = new EpisodeExtras(List.of(), List.of(), List.of(), List.of(), null, null);

    public EpisodeExtras {
        directors = TvTexts.cleanAll(directors, TvTexts.MAX_NAME_LENGTH, TvTexts.MAX_ENTRIES);
        writers = TvTexts.cleanAll(writers, TvTexts.MAX_NAME_LENGTH, TvTexts.MAX_ENTRIES);
        actors = actors == null ? List.of() : actors.stream()
                .filter(actor -> actor.name() != null)
                .limit(TvTexts.MAX_ACTORS)
                .toList();
        ratings = ratings == null ? List.of() : ratings.stream()
                .filter(rating -> rating.source() != null)
                .toList();
        playCount = playCount != null && playCount >= 0 ? playCount : null;
    }
}
