package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Content of an {@code <episodedetails>} element. The NFO of a multi-episode file contains several of them.
 *
 * @param season     season; {@code null} if the NFO has none
 * @param episode    episode; {@code null} if the NFO has none
 * @param aired      first aired date
 * @param runtime    runtime in minutes
 * @param videoSource video source, e.g. {@code dvd} (like Ember)
 * @param writers    writers ({@code <credits>})
 * @param actors     actors including guest stars
 * @param userRating own rating 1–10 or {@code null}
 * @param lastPlayed last playback in local time
 * @param dateAdded  date added in local time
 * @param streams    video, audio and subtitle streams; empty if unknown
 */
public record EpisodeNfo(
        String title,
        String originalTitle,
        Integer season,
        Integer episode,
        String plot,
        LocalDate aired,
        Integer runtime,
        String videoSource,
        Map<String, String> uniqueIds,
        List<NfoRating> ratings,
        List<String> directors,
        List<String> writers,
        List<NfoActor> actors,
        Integer userRating,
        Integer playCount,
        LocalDateTime lastPlayed,
        LocalDateTime dateAdded,
        StreamDetails streams) {

    public EpisodeNfo {
        uniqueIds = NfoCollections.orderedIds(uniqueIds);
        ratings = NfoCollections.list(ratings);
        directors = NfoCollections.list(directors);
        writers = NfoCollections.list(writers);
        actors = NfoCollections.list(actors);
        streams = streams == null ? StreamDetails.EMPTY : streams;
    }

    /** Whether this element belongs to the season and episode. */
    public boolean describes(int seasonNumber, int episodeNumber) {
        return season != null && episode != null && season == seasonNumber && episode == episodeNumber;
    }
}
