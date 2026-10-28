package de.tiktak.kmm.migration.ember.library;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.tiktak.kmm.migration.ember.EmberDatabases;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberSetLink;

/**
 * Liest den Filmbestand einer Ember-Datenbank (Schema v48). Verknüpfte Angaben werden je Tabelle
 * mit einer Abfrage gelesen und den Filmen zugeordnet, damit auch große Bibliotheken schnell gehen.
 */
public class EmberLibraryReader {

    private static final String SOURCES = "SELECT idSource, strName, strPath, strLanguage FROM moviesource ORDER BY idSource";
    private static final String MOVIES = """
            SELECT idMovie, idSource, MoviePath, Title, OriginalTitle, SortTitle, Year, premiered, Plot, Outline,
                   Tagline, Runtime, MPAA, Certification, Imdb, TMDB, TMDBColID, DateAdded, Playcount, iLastPlayed,
                   iUserRating, edition, VideoSource
            FROM movie ORDER BY idMovie""";
    private static final String UNIQUE_IDS = """
            SELECT media_id AS idMovie, type, value FROM uniqueid WHERE media_type = 'movie'
            ORDER BY media_id, isDefault DESC, idUniqueID""";
    private static final String GENRES = """
            SELECT l.idMovie, g.strGenre AS name FROM genrelinkmovie l JOIN genre g ON g.idGenre = l.idGenre
            ORDER BY l.idMovie, l.sorting, l.rowid""";
    private static final String ACTORS = """
            SELECT l.idMovie, a.strActor, l.strRole, a.strThumb FROM actorlinkmovie l JOIN actors a ON a.idActor = l.idActor
            ORDER BY l.idMovie, l.iOrder, l.rowid""";
    private static final String DIRECTORS = """
            SELECT l.idMovie, a.strActor AS name FROM directorlinkmovie l JOIN actors a ON a.idActor = l.idDirector
            ORDER BY l.idMovie, l.rowid""";
    private static final String WRITERS = """
            SELECT l.idMovie, a.strActor AS name FROM writerlinkmovie l JOIN actors a ON a.idActor = l.idWriter
            ORDER BY l.idMovie, l.rowid""";
    private static final String STUDIOS = """
            SELECT l.idMovie, s.strStudio AS name FROM studiolinkmovie l JOIN studio s ON s.idStudio = l.idStudio
            ORDER BY l.idMovie, l.rowid""";
    private static final String COUNTRIES = """
            SELECT l.idMovie, c.strCountry AS name FROM countrylinkmovie l JOIN country c ON c.idCountry = l.idCountry
            ORDER BY l.idMovie, l.rowid""";
    private static final String TAGS = """
            SELECT l.idMedia AS idMovie, t.strTag AS name FROM taglinks l JOIN tag t ON t.idTag = l.idTag
            WHERE l.media_type = 'movie' ORDER BY l.idMedia, l.sorting, l.rowid""";
    private static final String RATINGS = """
            SELECT media_id AS idMovie, rating_type, rating, rating_max, votes, isDefault FROM rating
            WHERE media_type = 'movie' ORDER BY media_id, isDefault DESC, idRating""";
    private static final String SET_LINKS = "SELECT idMovie, idSet, iOrder FROM setlinkmovie ORDER BY idMovie, idSet";
    private static final String SETS = """
            SELECT s.idSet, s.Title, s.Plot, s.TMDBColID, s.SortMethod,
                   (SELECT u.value FROM uniqueid u WHERE u.media_type = 'set' AND u.media_id = s.idSet AND u.type = 'tmdb'
                    LIMIT 1) AS tmdbId
            FROM sets s ORDER BY s.idSet""";
    private static final String EXCLUDED_DIRECTORIES = "SELECT Dirname FROM ExcludeDir ORDER BY Dirname";

    private static final String IMDB = "imdb";
    /** Embers {@code SortMethod_MovieSet.Title}. */
    private static final int SORTED_BY_TITLE = 1;
    private static final String TMDB = "tmdb";
    private static final String TMDB_COLLECTION = "tmdbcol";
    private static final String ID = "idMovie";
    private static final int NO_ORDER = -1;

    public EmberLibrary read(Path databaseFile) {
        try (Connection connection = EmberDatabases.openReadOnly(databaseFile)) {
            return new EmberLibrary(sources(connection), movies(connection), sets(connection),
                    excludedDirectories(connection));
        } catch (SQLException e) {
            throw EmberProfileException.unreadableDatabase(databaseFile, e);
        }
    }

    /** Nur die Filmquellen, z. B. um Pfad-Zuordnungen vorzuschlagen. */
    public List<EmberMovieSource> readSources(Path databaseFile) {
        try (Connection connection = EmberDatabases.openReadOnly(databaseFile)) {
            return sources(connection);
        } catch (SQLException e) {
            throw EmberProfileException.unreadableDatabase(databaseFile, e);
        }
    }

    private List<EmberMovieSource> sources(Connection connection) throws SQLException {
        return EmberQueries.query(connection, SOURCES, row -> new EmberMovieSource(row.getLong("idSource"),
                EmberValues.text(row, "strName"), EmberValues.text(row, "strPath"),
                EmberValues.text(row, "strLanguage")));
    }

    private List<EmberMovie> movies(Connection connection) throws SQLException {
        Map<Long, Map<String, String>> uniqueIds = EmberQueries.uniqueIds(connection, UNIQUE_IDS, ID);
        Map<Long, List<String>> genres = EmberQueries.names(connection, GENRES, ID);
        Map<Long, List<EmberActor>> actors = EmberQueries.grouped(connection, ACTORS, ID, EmberQueries::actor);
        Map<Long, List<String>> directors = EmberQueries.names(connection, DIRECTORS, ID);
        Map<Long, List<String>> writers = EmberQueries.names(connection, WRITERS, ID);
        Map<Long, List<String>> studios = EmberQueries.names(connection, STUDIOS, ID);
        Map<Long, List<String>> countries = EmberQueries.names(connection, COUNTRIES, ID);
        Map<Long, List<String>> tags = EmberQueries.names(connection, TAGS, ID);
        Map<Long, List<EmberRating>> ratings = EmberQueries.grouped(connection, RATINGS, ID, EmberQueries::rating);
        Map<Long, List<EmberSetLink>> sets = EmberQueries.grouped(connection, SET_LINKS, ID, row -> new EmberSetLink(
                row.getLong("idSet"), order(EmberValues.nullableInt(row, "iOrder"))));
        Map<Long, EmberStreams> streams = EmberStreamReader.movies(connection);
        return EmberQueries.query(connection, MOVIES, row -> {
            long id = row.getLong("idMovie");
            return EmberMovie.builder()
                    .id(id)
                    .sourceId(row.getLong("idSource"))
                    .path(EmberValues.text(row, "MoviePath"))
                    .title(EmberValues.text(row, "Title"))
                    .originalTitle(EmberValues.text(row, "OriginalTitle"))
                    .sortTitle(EmberValues.text(row, "SortTitle"))
                    .year(EmberValues.positiveInteger(row, "Year"))
                    .premiered(EmberValues.date(row, "premiered"))
                    .plot(EmberValues.text(row, "Plot"))
                    .outline(EmberValues.text(row, "Outline"))
                    .tagline(EmberValues.text(row, "Tagline"))
                    .runtime(EmberValues.positiveInteger(row, "Runtime"))
                    .mpaa(firstPresent(EmberValues.text(row, "MPAA"), EmberValues.text(row, "Certification")))
                    .uniqueIds(withColumnIds(uniqueIds.getOrDefault(id, Map.of()), row))
                    .genres(genres.get(id))
                    .actors(actors.get(id))
                    .directors(directors.get(id))
                    .writers(writers.get(id))
                    .studios(studios.get(id))
                    .countries(countries.get(id))
                    .tags(tags.get(id))
                    .ratings(ratings.get(id))
                    .sets(sets.get(id))
                    .streams(streams.getOrDefault(id, EmberStreams.NONE))
                    .dateAdded(EmberValues.localDateTime(row, "DateAdded"))
                    .playCount(EmberValues.nullableInt(row, "Playcount"))
                    .lastPlayed(EmberValues.localDateTime(row, "iLastPlayed"))
                    .userRating(EmberValues.positiveInteger(row, "iUserRating"))
                    .edition(EmberValues.text(row, "edition"))
                    .videoSource(EmberValues.text(row, "VideoSource"))
                    .build();
        });
    }

    private List<EmberMovieSet> sets(Connection connection) throws SQLException {
        return EmberQueries.query(connection, SETS, row -> new EmberMovieSet(row.getLong("idSet"), EmberValues.text(row, "Title"),
                EmberValues.text(row, "Plot"),
                firstPresent(EmberValues.text(row, "TMDBColID"), EmberValues.text(row, "tmdbId")),
                Integer.valueOf(SORTED_BY_TITLE).equals(EmberValues.nullableInt(row, "SortMethod"))));
    }

    private List<String> excludedDirectories(Connection connection) throws SQLException {
        return EmberQueries.query(connection, EXCLUDED_DIRECTORIES, row -> EmberValues.text(row, "Dirname")).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /** Ältere Einträge haben die IDs nur in den Spalten der Filmtabelle. */
    private static Map<String, String> withColumnIds(Map<String, String> uniqueIds, ResultSet row) throws SQLException {
        Map<String, String> ids = new LinkedHashMap<>(uniqueIds);
        EmberQueries.putIfAbsent(ids, IMDB, EmberValues.text(row, "Imdb"));
        EmberQueries.putIfAbsent(ids, TMDB, EmberValues.text(row, "TMDB"));
        EmberQueries.putIfAbsent(ids, TMDB_COLLECTION, EmberValues.text(row, "TMDBColID"));
        return ids;
    }

    private static Integer order(Integer order) {
        return order == null || order == NO_ORDER ? null : order;
    }

    private static String firstPresent(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }
}
