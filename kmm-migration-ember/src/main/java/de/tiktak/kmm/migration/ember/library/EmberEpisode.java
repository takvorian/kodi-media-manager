package de.tiktak.kmm.migration.ember.library;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import lombok.Builder;

/**
 * Eine Episode aus der Ember-Datenbank, zu der eine Datei gehört. Mehrfachepisoden
 * ({@code S01E01E02}) sind mehrere Episoden mit derselben Datei.
 *
 * @param path      Pfad der Episodendatei, wie Ember ihn gespeichert hat (Windows)
 * @param uniqueIds IDs je Anbieter, Schlüssel klein geschrieben
 * @param actors    Darsteller, danach die Gaststars
 */
@Builder
public record EmberEpisode(
        long id,
        long showId,
        String path,
        int season,
        int episode,
        String title,
        String originalTitle,
        String plot,
        LocalDate aired,
        Integer runtime,
        Map<String, String> uniqueIds,
        List<EmberActor> actors,
        List<String> directors,
        List<String> writers,
        List<EmberRating> ratings,
        LocalDateTime dateAdded,
        Integer playCount,
        LocalDateTime lastPlayed,
        Integer userRating,
        EmberStreams streams) {

    public EmberEpisode {
        uniqueIds = uniqueIds == null ? Map.of() : Map.copyOf(uniqueIds);
        actors = copy(actors);
        directors = copy(directors);
        writers = copy(writers);
        ratings = copy(ratings);
        streams = streams == null ? EmberStreams.NONE : streams;
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
