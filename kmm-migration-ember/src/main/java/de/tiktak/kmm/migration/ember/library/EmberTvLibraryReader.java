package de.tiktak.kmm.migration.ember.library;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import de.tiktak.kmm.migration.ember.EmberDatabases;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;

/**
 * Liest den Serienbestand einer Ember-Datenbank (Schema v48). Episoden ohne Datei kennt Ember nur
 * aus der Online-Suche; sie werden gezählt, aber nicht übernommen.
 */
public class EmberTvLibraryReader {

    private static final String SOURCES = "SELECT idSource, strName, strPath, strLanguage FROM tvshowsource ORDER BY idSource";
    private static final String SHOWS = """
            SELECT idShow, idSource, TVShowPath, Title, strOriginalTitle, SortTitle, Premiered, Plot, Runtime, MPAA,
                   Certification, Status, TVDB, strIMDB, strTMDB, iUserRating
            FROM tvshow ORDER BY idShow""";
    private static final String SHOW_UNIQUE_IDS = """
            SELECT media_id AS idShow, type, value FROM uniqueid WHERE media_type = 'tvshow'
            ORDER BY media_id, isDefault DESC, idUniqueID""";
    private static final String SHOW_GENRES = """
            SELECT l.idShow, g.strGenre AS name FROM genrelinktvshow l JOIN genre g ON g.idGenre = l.idGenre
            ORDER BY l.idShow, l.sorting, l.rowid""";
    private static final String SHOW_STUDIOS = """
            SELECT l.idShow, s.strStudio AS name FROM studiolinktvshow l JOIN studio s ON s.idStudio = l.idStudio
            ORDER BY l.idShow, l.rowid""";
    private static final String SHOW_TAGS = """
            SELECT l.idMedia AS idShow, t.strTag AS name FROM taglinks l JOIN tag t ON t.idTag = l.idTag
            WHERE l.media_type = 'tvshow' ORDER BY l.idMedia, l.sorting, l.rowid""";
    private static final String SHOW_ACTORS = """
            SELECT l.idShow, a.strActor, l.strRole, a.strThumb FROM actorlinktvshow l JOIN actors a ON a.idActor = l.idActor
            ORDER BY l.idShow, l.iOrder, l.rowid""";
    private static final String SHOW_RATINGS = """
            SELECT media_id AS idShow, rating_type, rating, rating_max, votes, isDefault FROM rating
            WHERE media_type = 'tvshow' ORDER BY media_id, isDefault DESC, idRating""";
    private static final String SEASON_TITLES = """
            SELECT idShow, Season, Title FROM seasons WHERE Season >= 0 AND Title IS NOT NULL ORDER BY idShow, Season""";
    private static final String EPISODES = """
            SELECT e.idEpisode, e.idShow, f.strFilename, e.Season, e.Episode, e.Title, e.OriginalTitle, e.Plot,
                   e.Aired, e.Runtime, e.strIMDB, e.strTMDB, e.strTVDB, e.DateAdded, e.Playcount, e.iLastPlayed,
                   e.iUserRating, e.VideoSource
            FROM episode e LEFT JOIN files f ON f.idFile = e.idFile
            ORDER BY e.idShow, e.Season, e.Episode, e.idEpisode""";
    private static final String EPISODE_UNIQUE_IDS = """
            SELECT media_id AS idEpisode, type, value FROM uniqueid WHERE media_type = 'episode'
            ORDER BY media_id, isDefault DESC, idUniqueID""";
    private static final String EPISODE_ACTORS = """
            SELECT idEpisode, strActor, strRole, strThumb FROM (
                SELECT l.idEpisode, a.strActor, l.strRole, a.strThumb, 0 AS guest, l.iOrder, l.rowid AS position
                FROM actorlinkepisode l JOIN actors a ON a.idActor = l.idActor
                UNION ALL
                SELECT l.idEpisode, a.strActor, l.strRole, a.strThumb, 1 AS guest, l.iOrder, l.rowid AS position
                FROM gueststarlinkepisode l JOIN actors a ON a.idActor = l.idActor)
            ORDER BY idEpisode, guest, iOrder, position""";
    private static final String EPISODE_DIRECTORS = """
            SELECT l.idEpisode, a.strActor AS name FROM directorlinkepisode l JOIN actors a ON a.idActor = l.idDirector
            ORDER BY l.idEpisode, l.rowid""";
    private static final String EPISODE_WRITERS = """
            SELECT l.idEpisode, a.strActor AS name FROM writerlinkepisode l JOIN actors a ON a.idActor = l.idWriter
            ORDER BY l.idEpisode, l.rowid""";
    private static final String EPISODE_RATINGS = """
            SELECT media_id AS idEpisode, rating_type, rating, rating_max, votes, isDefault FROM rating
            WHERE media_type = 'episode' ORDER BY media_id, isDefault DESC, idRating""";

    private static final String SHOW_ID = "idShow";
    private static final String EPISODE_ID = "idEpisode";
    private static final String IMDB = "imdb";
    private static final String TMDB = "tmdb";
    private static final String TVDB = "tvdb";

    public EmberTvLibrary read(Path databaseFile) {
        try (Connection connection = EmberDatabases.openReadOnly(databaseFile)) {
            List<EmberEpisode> episodes = new ArrayList<>();
            int withoutFile = episodes(connection, episodes);
            return new EmberTvLibrary(sources(connection), shows(connection), episodes, withoutFile);
        } catch (SQLException e) {
            throw EmberProfileException.unreadableDatabase(databaseFile, e);
        }
    }

    /** Nur die Serienquellen, z. B. um Pfad-Zuordnungen vorzuschlagen. */
    public List<EmberTvShowSource> readSources(Path databaseFile) {
        try (Connection connection = EmberDatabases.openReadOnly(databaseFile)) {
            return sources(connection);
        } catch (SQLException e) {
            throw EmberProfileException.unreadableDatabase(databaseFile, e);
        }
    }

    private List<EmberTvShowSource> sources(Connection connection) throws SQLException {
        return EmberQueries.query(connection, SOURCES, row -> new EmberTvShowSource(row.getLong("idSource"),
                EmberValues.text(row, "strName"), EmberValues.text(row, "strPath"),
                EmberValues.text(row, "strLanguage")));
    }

    private List<EmberTvShow> shows(Connection connection) throws SQLException {
        Map<Long, Map<String, String>> uniqueIds = EmberQueries.uniqueIds(connection, SHOW_UNIQUE_IDS, SHOW_ID);
        Map<Long, List<String>> genres = EmberQueries.names(connection, SHOW_GENRES, SHOW_ID);
        Map<Long, List<String>> studios = EmberQueries.names(connection, SHOW_STUDIOS, SHOW_ID);
        Map<Long, List<String>> tags = EmberQueries.names(connection, SHOW_TAGS, SHOW_ID);
        Map<Long, List<EmberActor>> actors = EmberQueries.grouped(connection, SHOW_ACTORS, SHOW_ID,
                EmberQueries::actor);
        Map<Long, List<EmberRating>> ratings = EmberQueries.grouped(connection, SHOW_RATINGS, SHOW_ID,
                EmberQueries::rating);
        Map<Long, SortedMap<Integer, String>> seasonTitles = seasonTitles(connection);
        return EmberQueries.query(connection, SHOWS, row -> {
            long id = row.getLong(SHOW_ID);
            return EmberTvShow.builder()
                    .id(id)
                    .sourceId(row.getLong("idSource"))
                    .path(EmberValues.text(row, "TVShowPath"))
                    .title(EmberValues.text(row, "Title"))
                    .originalTitle(EmberValues.text(row, "strOriginalTitle"))
                    .sortTitle(EmberValues.text(row, "SortTitle"))
                    .premiered(EmberValues.date(row, "Premiered"))
                    .plot(EmberValues.text(row, "Plot"))
                    .runtime(EmberValues.positiveInteger(row, "Runtime"))
                    .mpaa(firstPresent(EmberValues.text(row, "MPAA"), EmberValues.text(row, "Certification")))
                    .status(EmberValues.text(row, "Status"))
                    .uniqueIds(withColumnIds(uniqueIds.getOrDefault(id, Map.of()), row, "TVDB"))
                    .genres(genres.get(id))
                    .studios(studios.get(id))
                    .tags(tags.get(id))
                    .actors(actors.get(id))
                    .ratings(ratings.get(id))
                    .seasonTitles(seasonTitles.get(id))
                    .userRating(EmberValues.positiveInteger(row, "iUserRating"))
                    .build();
        });
    }

    private Map<Long, SortedMap<Integer, String>> seasonTitles(Connection connection) throws SQLException {
        Map<Long, SortedMap<Integer, String>> titles = new HashMap<>();
        EmberQueries.forEachRow(connection, SEASON_TITLES, row -> {
            String title = EmberValues.text(row, "Title");
            if (title != null) {
                titles.computeIfAbsent(row.getLong(SHOW_ID), key -> new TreeMap<>()).put(row.getInt("Season"), title);
            }
        });
        return titles;
    }

    /** Liest die Episoden mit Datei in {@code target} und liefert die Zahl der Episoden ohne Datei. */
    private int episodes(Connection connection, List<EmberEpisode> target) throws SQLException {
        Map<Long, Map<String, String>> uniqueIds = EmberQueries.uniqueIds(connection, EPISODE_UNIQUE_IDS, EPISODE_ID);
        Map<Long, List<EmberActor>> actors = EmberQueries.grouped(connection, EPISODE_ACTORS, EPISODE_ID,
                EmberQueries::actor);
        Map<Long, List<String>> directors = EmberQueries.names(connection, EPISODE_DIRECTORS, EPISODE_ID);
        Map<Long, List<String>> writers = EmberQueries.names(connection, EPISODE_WRITERS, EPISODE_ID);
        Map<Long, List<EmberRating>> ratings = EmberQueries.grouped(connection, EPISODE_RATINGS, EPISODE_ID,
                EmberQueries::rating);
        Map<Long, EmberStreams> streams = EmberStreamReader.episodes(connection);
        int[] withoutFile = {0};
        EmberQueries.forEachRow(connection, EPISODES, row -> {
            String path = EmberValues.text(row, "strFilename");
            if (path == null) {
                withoutFile[0]++;
                return;
            }
            long id = row.getLong(EPISODE_ID);
            target.add(EmberEpisode.builder()
                    .id(id)
                    .showId(row.getLong(SHOW_ID))
                    .path(path)
                    .season(row.getInt("Season"))
                    .episode(row.getInt("Episode"))
                    .title(EmberValues.text(row, "Title"))
                    .originalTitle(EmberValues.text(row, "OriginalTitle"))
                    .plot(EmberValues.text(row, "Plot"))
                    .aired(EmberValues.date(row, "Aired"))
                    .runtime(EmberValues.positiveInteger(row, "Runtime"))
                    .uniqueIds(withColumnIds(uniqueIds.getOrDefault(id, Map.of()), row, "strTVDB"))
                    .actors(actors.get(id))
                    .directors(directors.get(id))
                    .writers(writers.get(id))
                    .ratings(ratings.get(id))
                    .dateAdded(EmberValues.localDateTime(row, "DateAdded"))
                    .playCount(EmberValues.nullableInt(row, "Playcount"))
                    .lastPlayed(EmberValues.localDateTime(row, "iLastPlayed"))
                    .userRating(EmberValues.positiveInteger(row, "iUserRating"))
                    .videoSource(EmberValues.text(row, "VideoSource"))
                    .streams(streams.getOrDefault(id, EmberStreams.NONE))
                    .build());
        });
        return withoutFile[0];
    }

    /** Ältere Einträge haben die IDs nur in den Spalten der Serien- bzw. Episodentabelle. */
    private static Map<String, String> withColumnIds(Map<String, String> uniqueIds, ResultSet row, String tvdbColumn)
            throws SQLException {
        Map<String, String> ids = new LinkedHashMap<>(uniqueIds);
        EmberQueries.putIfAbsent(ids, TVDB, EmberValues.text(row, tvdbColumn));
        EmberQueries.putIfAbsent(ids, TMDB, EmberValues.text(row, "strTMDB"));
        EmberQueries.putIfAbsent(ids, IMDB, EmberValues.text(row, "strIMDB"));
        return ids;
    }

    private static String firstPresent(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }
}
