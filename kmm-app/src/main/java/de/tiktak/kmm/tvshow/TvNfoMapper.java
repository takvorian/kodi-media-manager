package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoRating;
import de.tiktak.kmm.nfo.TvShowNfo;

/**
 * Übersetzt zwischen den NFO-Modellen aus {@code kmm-nfo} und den Angaben von Serien und Episoden.
 * Zeitpunkte stehen in NFOs als lokale Uhrzeit.
 */
final class TvNfoMapper {

    static final String TVDB = "tvdb";
    static final String TMDB = "tmdb";
    static final String IMDB = "imdb";

    private TvNfoMapper() {
    }

    static TvShowMetadata toMetadata(TvShowNfo nfo, String fallbackTitle, Integer fallbackYear) {
        return new TvShowMetadata(
                nfo.title() != null ? nfo.title() : fallbackTitle,
                nfo.originalTitle(),
                nfo.sortTitle(),
                nfo.premiered() != null ? Integer.valueOf(nfo.premiered().getYear()) : fallbackYear,
                nfo.premiered(),
                nfo.plot(),
                nfo.runtime(),
                nfo.mpaa(),
                nfo.status(),
                nfo.genres(),
                nfo.studios(),
                nfo.tags(),
                nfo.uniqueIds().get(TVDB),
                nfo.uniqueIds().get(TMDB),
                nfo.uniqueIds().get(IMDB),
                nfo.userRating()).withinLimits();
    }

    static TvShowExtras toExtras(TvShowNfo nfo) {
        return new TvShowExtras(actors(nfo.actors()), ratings(nfo.ratings()), nfo.seasonNames());
    }

    static TvShowNfo toNfo(TvShowMetadata metadata, TvShowExtras extras) {
        return new TvShowNfo(metadata.title(), metadata.originalTitle(), metadata.sortTitle(), metadata.premiered(),
                metadata.plot(), metadata.runtime(), metadata.mpaa(), metadata.status(), metadata.genres(),
                metadata.studios(), metadata.tags(),
                ids(metadata.tvdbId(), metadata.tmdbId(), metadata.imdbId()),
                nfoRatings(extras.ratings()), nfoActors(extras.actors()), extras.seasonNames(),
                metadata.userRating());
    }

    static EpisodeMetadata toMetadata(EpisodeNfo nfo, String fallbackTitle) {
        return new EpisodeMetadata(
                nfo.title() != null ? nfo.title() : fallbackTitle,
                nfo.originalTitle(),
                nfo.plot(),
                nfo.aired(),
                nfo.runtime(),
                nfo.uniqueIds().get(TVDB),
                nfo.uniqueIds().get(TMDB),
                nfo.uniqueIds().get(IMDB),
                nfo.userRating(), nfo.videoSource()).withinLimits();
    }

    static EpisodeExtras toExtras(EpisodeNfo nfo, ZoneId zone) {
        return new EpisodeExtras(nfo.directors(), nfo.writers(), actors(nfo.actors()), ratings(nfo.ratings()),
                nfo.playCount(), toInstant(nfo.lastPlayed(), zone), nfo.streams());
    }

    static EpisodeNfo toNfo(int season, int episode, EpisodeMetadata metadata, EpisodeExtras extras,
            Instant addedAt, ZoneId zone) {
        return new EpisodeNfo(metadata.title(), metadata.originalTitle(), season, episode, metadata.plot(),
                metadata.aired(), metadata.runtime(), metadata.videoSource(),
                ids(metadata.tvdbId(), metadata.tmdbId(), metadata.imdbId()),
                nfoRatings(extras.ratings()), extras.directors(), extras.writers(), nfoActors(extras.actors()),
                metadata.userRating(), extras.playCount(), toLocal(extras.lastPlayed(), zone),
                toLocal(addedAt, zone), extras.streams());
    }

    static Instant toInstant(LocalDateTime local, ZoneId zone) {
        return local == null ? null : local.atZone(zone).toInstant();
    }

    static LocalDateTime toLocal(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    /** Reihenfolge TVDB, TMDB, IMDb wie bei Kodis Serien-Scrapern; die erste ID wird Standard. */
    private static Map<String, String> ids(String tvdbId, String tmdbId, String imdbId) {
        Map<String, String> ids = new LinkedHashMap<>();
        putIfPresent(ids, TVDB, tvdbId);
        putIfPresent(ids, TMDB, tmdbId);
        putIfPresent(ids, IMDB, imdbId);
        return ids;
    }

    private static void putIfPresent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.put(type, value);
        }
    }

    private static List<TvActor> actors(List<NfoActor> actors) {
        return actors.stream().map(actor -> new TvActor(actor.name(), actor.role(), actor.thumb())).toList();
    }

    private static List<TvRating> ratings(List<NfoRating> ratings) {
        return ratings.stream()
                .map(rating -> new TvRating(rating.name(), rating.value(), rating.max(), rating.votes(),
                        rating.defaultValue()))
                .toList();
    }

    private static List<NfoActor> nfoActors(List<TvActor> actors) {
        return IntStream.range(0, actors.size())
                .mapToObj(index -> new NfoActor(actors.get(index).name(), actors.get(index).role(), index,
                        actors.get(index).thumb()))
                .toList();
    }

    private static List<NfoRating> nfoRatings(List<TvRating> ratings) {
        return ratings.stream()
                .map(rating -> new NfoRating(rating.source(), rating.value(), rating.max(), rating.votes(),
                        rating.defaultRating()))
                .toList();
    }
}
