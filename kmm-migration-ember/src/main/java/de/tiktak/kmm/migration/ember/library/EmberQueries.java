package de.tiktak.kmm.migration.ember.library;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helpers for reading the Ember database: linked data is read with one query per table and assigned via an ID
 * column.
 */
final class EmberQueries {

    private EmberQueries() {
    }

    static <T> List<T> query(Connection connection, String sql, RowMapper<T> mapper) throws SQLException {
        List<T> values = new ArrayList<>();
        forEachRow(connection, sql, row -> values.add(mapper.map(row)));
        return values;
    }

    /** Assigns the rows via the column {@code idColumn}; rows for which the mapper returns {@code null} are dropped. */
    static <T> Map<Long, List<T>> grouped(Connection connection, String sql, String idColumn, RowMapper<T> mapper)
            throws SQLException {
        Map<Long, List<T>> grouped = new HashMap<>();
        forEachRow(connection, sql, row -> {
            T value = mapper.map(row);
            if (value != null) {
                grouped.computeIfAbsent(row.getLong(idColumn), key -> new ArrayList<>()).add(value);
            }
        });
        return grouped;
    }

    static Map<Long, List<String>> names(Connection connection, String sql, String idColumn) throws SQLException {
        return grouped(connection, sql, idColumn, row -> EmberValues.text(row, "name"));
    }

    /** IDs per entry as {@code type -> value}; the default ID first, the first value per type wins. */
    static Map<Long, Map<String, String>> uniqueIds(Connection connection, String sql, String idColumn)
            throws SQLException {
        Map<Long, Map<String, String>> ids = new HashMap<>();
        forEachRow(connection, sql, row -> {
            String type = EmberValues.text(row, "type");
            String value = EmberValues.text(row, "value");
            if (type != null && value != null) {
                ids.computeIfAbsent(row.getLong(idColumn), key -> new LinkedHashMap<>())
                        .putIfAbsent(type.toLowerCase(Locale.ROOT), value);
            }
        });
        return ids;
    }

    static void forEachRow(Connection connection, String sql, RowConsumer consumer) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            while (row.next()) {
                consumer.accept(row);
            }
        }
    }

    static EmberMovie.EmberActor actor(ResultSet row) throws SQLException {
        String name = EmberValues.text(row, "strActor");
        return name == null ? null
                : new EmberMovie.EmberActor(name, EmberValues.text(row, "strRole"), EmberValues.text(row, "strThumb"));
    }

    static EmberMovie.EmberRating rating(ResultSet row) throws SQLException {
        String source = EmberValues.text(row, "rating_type");
        return source == null ? null : new EmberMovie.EmberRating(source, row.getDouble("rating"),
                EmberValues.nullableInt(row, "rating_max"), EmberValues.nullableInt(row, "votes"),
                row.getBoolean("isDefault"));
    }

    static void putIfAbsent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.putIfAbsent(type, value);
        }
    }

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    @FunctionalInterface
    interface RowConsumer {
        void accept(ResultSet row) throws SQLException;
    }
}
