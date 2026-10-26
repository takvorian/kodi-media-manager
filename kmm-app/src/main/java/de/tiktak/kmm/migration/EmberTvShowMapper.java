package de.tiktak.kmm.migration;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

import de.tiktak.kmm.migration.ember.library.EmberEpisode;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import de.tiktak.kmm.migration.ember.library.EmberTvShow;
import de.tiktak.kmm.tvshow.EpisodeExtras;
import de.tiktak.kmm.tvshow.EpisodeImport;
import de.tiktak.kmm.tvshow.EpisodeMetadata;
import de.tiktak.kmm.tvshow.TvActor;
import de.tiktak.kmm.tvshow.TvRating;
import de.tiktak.kmm.tvshow.TvShowExtras;
import de.tiktak.kmm.tvshow.TvShowImport;
import de.tiktak.kmm.tvshow.TvShowMetadata;

/**
 * Übersetzt Serien und Episoden aus Ember in KMM-Importe.
 */
final class EmberTvShowMapper {

    private static final String IMDB = "imdb";
    private static final String TMDB = "tmdb";
    private static final String TVDB = "tvdb";
    private static final String DEFAULT_EPISODE_TITLE = "Episode %d";

    private EmberTvShowMapper() {
    }

    /**
     * @param directoryPath Ordner der Serie im Zielsystem
     */
    static TvShowImport toImport(EmberTvShow show, long sourceId, String directoryPath) {
        TvShowMetadata metadata = new TvShowMetadata(
                show.title() != null ? show.title() : lastSegment(directoryPath),
                show.originalTitle(),
                show.sortTitle(),
                null,
                show.premiered(),
                show.plot(),
                show.runtime(),
                EmberMappings.certification(show.mpaa()),
                show.status(),
                show.genres(),
                show.studios(),
                show.tags(),
                show.uniqueIds().get(TVDB),
                show.uniqueIds().get(TMDB),
                show.uniqueIds().get(IMDB),
                EmberMappings.userRating(show.userRating())).withinLimits();
        TvShowExtras extras = new TvShowExtras(actors(show.actors()), ratings(show.ratings()), show.seasonTitles());
        return new TvShowImport(sourceId, directoryPath, metadata, extras);
    }

    /**
     * @param filePath Pfad der Episodendatei im Zielsystem
     * @param zone     Zeitzone, in der Ember lief; Ember speichert Zeitpunkte als lokale Uhrzeit
     */
    static EpisodeImport toImport(EmberEpisode episode, String filePath, ZoneId zone) {
        EpisodeMetadata metadata = new EpisodeMetadata(
                episode.title() != null ? episode.title() : DEFAULT_EPISODE_TITLE.formatted(episode.episode()),
                episode.originalTitle(),
                episode.plot(),
                episode.aired(),
                episode.runtime(),
                episode.uniqueIds().get(TVDB),
                episode.uniqueIds().get(TMDB),
                episode.uniqueIds().get(IMDB),
                EmberMappings.userRating(episode.userRating())).withinLimits();
        EpisodeExtras extras = new EpisodeExtras(
                episode.directors(),
                episode.writers(),
                actors(episode.actors()),
                ratings(episode.ratings()),
                episode.playCount(),
                EmberMappings.toInstant(episode.lastPlayed(), zone),
                EmberStreamMapper.toStreamDetails(episode.streams()));
        return new EpisodeImport(filePath, episode.season(), episode.episode(), metadata, extras,
                EmberMappings.toInstant(episode.dateAdded(), zone));
    }

    /** Name einer Serie für Berichte. */
    static String displayName(EmberTvShow show) {
        return show.title() != null ? show.title() : show.path();
    }

    private static List<TvActor> actors(List<EmberActor> actors) {
        return actors.stream().map(actor -> new TvActor(actor.name(), actor.role(), actor.thumb())).toList();
    }

    private static List<TvRating> ratings(List<EmberRating> ratings) {
        return ratings.stream()
                .filter(EmberMappings::isRated)
                .map(rating -> new TvRating(rating.source(), rating.value(), rating.max(), rating.votes(),
                        rating.defaultRating()))
                .toList();
    }

    private static String lastSegment(String directoryPath) {
        Path name = Path.of(directoryPath).getFileName();
        return name == null ? directoryPath : name.toString();
    }
}
