package de.tiktak.kmm.movie;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoMovieSet;
import de.tiktak.kmm.nfo.NfoRating;
import de.tiktak.kmm.nfo.StreamDetails;

/**
 * Übersetzt zwischen NFO-Inhalt und Film-Metadaten. Zeitangaben stehen in der NFO in lokaler Zeit.
 */
final class MovieNfoMapper {

    private static final int NO_USER_RATING = 0;
    private static final Comparator<NfoActor> BY_ORDER = Comparator.comparing(NfoActor::order,
            Comparator.nullsLast(Comparator.naturalOrder()));

    private MovieNfoMapper() {
    }

    /**
     * Übernimmt die NFO; fehlende Werte werden mit den Werten aus dem Scan ergänzt.
     *
     * @param fallbackTitle aus dem Dateinamen abgeleiteter Titel, falls die NFO keinen enthält
     * @param fallbackYear  aus dem Dateinamen abgeleitetes Jahr, falls die NFO keines enthält
     */
    static MovieMetadata toMetadata(MovieNfo nfo, String fallbackTitle, Integer fallbackYear) {
        Integer year = nfo.effectiveYear();
        return new MovieMetadata(
                nfo.title() == null ? fallbackTitle : nfo.title(),
                nfo.originalTitle(),
                nfo.sortTitle(),
                year == null ? fallbackYear : year,
                nfo.premiered(),
                nfo.plot(),
                nfo.outline(),
                nfo.tagline(),
                nfo.runtime(),
                nfo.mpaa(),
                nfo.genres(),
                nfo.uniqueId(MovieNfo.IMDB).orElse(null),
                nfo.uniqueId(MovieNfo.TMDB).orElse(null));
    }

    /**
     * Erzeugt den NFO-Inhalt. IDs weiterer Anbieter aus der vorhandenen NFO (z. B. {@code tvdb})
     * bleiben erhalten; IMDb und TMDB kommen ausschließlich aus den Metadaten.
     */
    static MovieNfo toNfo(MovieMetadata metadata, MovieNfoSupplement supplement,
            Map<String, String> existingUniqueIds) {
        Map<String, String> uniqueIds = new LinkedHashMap<>();
        putIfPresent(uniqueIds, MovieNfo.IMDB, metadata.imdbId());
        putIfPresent(uniqueIds, MovieNfo.TMDB, metadata.tmdbId());
        existingUniqueIds.forEach((type, value) -> {
            if (!MovieNfo.IMDB.equals(type) && !MovieNfo.TMDB.equals(type)) {
                uniqueIds.putIfAbsent(type, value);
            }
        });
        return new MovieNfo(
                metadata.title(),
                metadata.originalTitle(),
                metadata.sortTitle(),
                metadata.year(),
                metadata.premiered(),
                metadata.plot(),
                metadata.outline(),
                metadata.tagline(),
                metadata.runtime(),
                metadata.mpaa(),
                metadata.genres(),
                uniqueIds,
                supplement);
    }

    /** Darsteller werden nach ihrer Reihenfolge in der NFO sortiert; eine eigene Bewertung von 0 heißt „keine“. */
    static MovieSupplement toSupplement(MovieNfoSupplement nfo, ZoneId zone) {
        return new MovieSupplement(
                nfo.actors().stream().sorted(BY_ORDER)
                        .map(actor -> new MovieSupplement.Actor(actor.name(), actor.role(), actor.thumb()))
                        .toList(),
                nfo.directors(),
                nfo.writers(),
                nfo.studios(),
                nfo.countries(),
                nfo.tags(),
                nfo.ratings().stream()
                        .map(rating -> new MovieSupplement.Rating(rating.name(), rating.value(), rating.max(),
                                rating.votes(), rating.defaultValue()))
                        .toList(),
                nfo.set() == null ? null : new MovieSupplement.SetReference(nfo.set().name(), nfo.set().overview()),
                nfo.userRating() == null || nfo.userRating() == NO_USER_RATING ? null : nfo.userRating(),
                nfo.playCount(),
                toInstant(nfo.lastPlayed(), zone));
    }

    static MovieNfoSupplement toNfoSupplement(MovieSupplement supplement, StreamDetails streams, Instant addedAt,
            ZoneId zone) {
        List<MovieSupplement.Actor> actors = supplement.actors();
        return new MovieNfoSupplement(
                IntStream.range(0, actors.size())
                        .mapToObj(index -> new NfoActor(actors.get(index).name(), actors.get(index).role(), index,
                                actors.get(index).thumb()))
                        .toList(),
                supplement.directors(),
                supplement.writers(),
                supplement.studios(),
                supplement.countries(),
                supplement.tags(),
                supplement.ratings().stream()
                        .map(rating -> new NfoRating(rating.source(), rating.value(), rating.max(), rating.votes(),
                                rating.defaultRating()))
                        .toList(),
                supplement.set() == null ? null : new NfoMovieSet(supplement.set().name(), supplement.set().overview()),
                supplement.userRating(),
                supplement.playCount(),
                toLocal(supplement.lastPlayed(), zone),
                toLocal(addedAt, zone),
                streams);
    }

    static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }

    private static LocalDateTime toLocal(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    private static void putIfPresent(Map<String, String> uniqueIds, String type, String value) {
        if (value != null) {
            uniqueIds.put(type, value);
        }
    }
}
