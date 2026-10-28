package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Inhalt eines {@code <episodedetails>}-Elements. Eine NFO einer Mehrfachepisode enthält mehrere davon.
 *
 * @param season     Staffel; {@code null}, wenn die NFO keine angibt
 * @param episode    Episode; {@code null}, wenn die NFO keine angibt
 * @param aired      Erstausstrahlung
 * @param runtime    Laufzeit in Minuten
 * @param videoSource Videoquelle, z. B. {@code dvd} (wie Ember)
 * @param writers    Drehbuch ({@code <credits>})
 * @param actors     Darsteller einschließlich Gaststars
 * @param userRating eigene Bewertung 1–10 oder {@code null}
 * @param lastPlayed letzte Wiedergabe in lokaler Zeit
 * @param dateAdded  Hinzugefügt-Datum in lokaler Zeit
 * @param streams    Video-, Ton- und Untertitelspuren; leer, wenn unbekannt
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

    /** Ob dieses Element zu Staffel und Episode gehört. */
    public boolean describes(int seasonNumber, int episodeNumber) {
        return season != null && episode != null && season == seasonNumber && episode == episodeNumber;
    }
}
