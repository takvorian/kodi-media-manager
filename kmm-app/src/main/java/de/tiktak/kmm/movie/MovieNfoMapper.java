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
 * Translates between NFO content and movie metadata. Time values in the NFO are in local time.
 */
final class MovieNfoMapper {

    private static final int NO_USER_RATING = 0;
    private static final Comparator<NfoActor> BY_ORDER = Comparator.comparing(NfoActor::order,
            Comparator.nullsLast(Comparator.naturalOrder()));

    private MovieNfoMapper() {
    }

    /**
     * Takes over the NFO; missing values are supplemented with the values from the scan.
     *
     * @param fallbackTitle title derived from the file name, in case the NFO has none
     * @param fallbackYear  year derived from the file name, in case the NFO has none
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
                nfo.uniqueId(MovieNfo.TMDB).orElse(null), nfo.edition(), nfo.videoSource());
    }

    /**
     * Creates the NFO content. IDs of further providers from the existing NFO (e.g. {@code tvdb})
     * are kept; IMDb and TMDB come exclusively from the metadata.
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
                metadata.edition(),
                metadata.videoSource(),
                metadata.genres(),
                uniqueIds,
                supplement);
    }

    /** Cast members are sorted by their order in the NFO; an own rating of 0 means "none". */
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
                nfo.set() == null ? null : new MovieSupplement.SetReference(nfo.set().name(), nfo.set().overview(),
                        nfo.set().tmdbId()),
                nfo.userRating() == null || nfo.userRating() == NO_USER_RATING ? null : nfo.userRating(),
                nfo.playCount(),
                toInstant(nfo.lastPlayed(), zone));
    }

    /**
     * @param trailer trailer link, {@link MovieNfoSupplement#NO_TRAILER} or {@code null} if unknown
     */
    static MovieNfoSupplement toNfoSupplement(MovieSupplement supplement, StreamDetails streams, Instant addedAt,
            ZoneId zone, String trailer) {
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
                supplement.set() == null ? null : new NfoMovieSet(supplement.set().name(), supplement.set().overview(),
                        supplement.set().tmdbCollectionId()),
                supplement.userRating(),
                supplement.playCount(),
                toLocal(supplement.lastPlayed(), zone),
                toLocal(addedAt, zone),
                streams).withTrailer(trailer);
    }

    static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }

    static LocalDateTime toLocal(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    private static void putIfPresent(Map<String, String> uniqueIds, String type, String value) {
        if (value != null) {
            uniqueIds.put(type, value);
        }
    }
}
