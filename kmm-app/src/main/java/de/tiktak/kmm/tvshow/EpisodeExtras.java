package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.List;

import de.tiktak.kmm.nfo.StreamDetails;

/**
 * Further details of an episode that KMM takes over from the NFO and migration but does not edit yet.
 *
 * @param actors     cast including guest stars
 * @param playCount  number of plays or {@code null}
 * @param lastPlayed last play or {@code null}
 * @param streams    video, audio and subtitle tracks
 */
public record EpisodeExtras(
        List<String> directors,
        List<String> writers,
        List<TvActor> actors,
        List<TvRating> ratings,
        Integer playCount,
        Instant lastPlayed,
        StreamDetails streams) {

    public static final EpisodeExtras EMPTY = new EpisodeExtras(List.of(), List.of(), List.of(), List.of(), null, null,
            StreamDetails.EMPTY);

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
        streams = streams == null ? StreamDetails.EMPTY : streams;
    }

    /** The same details with a different cast. */
    public EpisodeExtras withActors(List<TvActor> newActors) {
        return new EpisodeExtras(directors, writers, newActors, ratings, playCount, lastPlayed, streams);
    }

    /** The same details with different technical details. */
    public EpisodeExtras withStreams(StreamDetails newStreams) {
        return new EpisodeExtras(directors, writers, actors, ratings, playCount, lastPlayed, newStreams);
    }
}
