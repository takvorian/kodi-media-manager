package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks an Ember database and determines the size of the library. The database is opened read-only, the Ember
 * profile stays unchanged.
 */
public class EmberDatabaseInspector {

    private static final String TABLE_NAMES_QUERY = "SELECT name FROM sqlite_master WHERE type = 'table'";
    private static final String COUNT_QUERY_TEMPLATE = "SELECT COUNT(*) FROM %s";

    public EmberLibraryStatistics inspect(Path databaseFile) {
        try (Connection connection = EmberDatabases.openReadOnly(databaseFile)) {
            requireEmberTables(connection, databaseFile);
            return new EmberLibraryStatistics(
                    count(connection, EmberTable.MOVIE),
                    count(connection, EmberTable.MOVIE_SET),
                    count(connection, EmberTable.TV_SHOW),
                    count(connection, EmberTable.EPISODE),
                    count(connection, EmberTable.MOVIE_SOURCE),
                    count(connection, EmberTable.TV_SHOW_SOURCE));
        } catch (SQLException e) {
            throw EmberProfileException.unreadableDatabase(databaseFile, e);
        }
    }

    private void requireEmberTables(Connection connection, Path databaseFile) throws SQLException {
        Set<String> existingTables = readTableNames(connection);
        List<String> missingTables = Arrays.stream(EmberTable.values())
                .map(EmberTable::getTableName)
                .filter(table -> !existingTables.contains(table))
                .toList();
        if (!missingTables.isEmpty()) {
            throw EmberProfileException.missingTables(databaseFile, missingTables);
        }
    }

    private Set<String> readTableNames(Connection connection) throws SQLException {
        Set<String> tableNames = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(TABLE_NAMES_QUERY)) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString(1));
            }
        }
        return tableNames;
    }

    private long count(Connection connection, EmberTable table) throws SQLException {
        // The table name comes from the enum, not from user input.
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(COUNT_QUERY_TEMPLATE.formatted(table.getTableName()))) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
