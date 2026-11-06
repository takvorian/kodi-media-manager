package de.tiktak.kmm.migration.ember;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

/**
 * Opens Ember databases read-only so the Ember profile stays unchanged.
 */
public final class EmberDatabases {

    private static final String JDBC_URL_PREFIX = "jdbc:sqlite:";

    private EmberDatabases() {
    }

    public static Connection openReadOnly(Path databaseFile) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.resetOpenMode(SQLiteOpenMode.CREATE);
        return config.createConnection(JDBC_URL_PREFIX + databaseFile.toAbsolutePath());
    }
}
