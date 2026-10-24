package de.tiktak.kmm.migration.ember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmberDatabaseInspectorTest {

    private final EmberDatabaseInspector inspector = new EmberDatabaseInspector();

    @TempDir
    Path directory;

    @Test
    void countsLibraryContent() {
        EmberFixtures.createDatabase(directory, EmberFixtures.DATABASE_FILE_NAME);

        EmberLibraryStatistics statistics = inspector.inspect(directory.resolve(EmberFixtures.DATABASE_FILE_NAME));

        assertThat(statistics).isEqualTo(new EmberLibraryStatistics(
                EmberFixtures.MOVIE_COUNT,
                EmberFixtures.MOVIE_SET_COUNT,
                EmberFixtures.TV_SHOW_COUNT,
                EmberFixtures.EPISODE_COUNT,
                EmberFixtures.MOVIE_SOURCE_COUNT,
                EmberFixtures.TV_SHOW_SOURCE_COUNT));
    }

    @Test
    void doesNotModifyDatabase() throws IOException {
        EmberFixtures.createDatabase(directory, EmberFixtures.DATABASE_FILE_NAME);
        Path databaseFile = directory.resolve(EmberFixtures.DATABASE_FILE_NAME);
        byte[] before = Files.readAllBytes(databaseFile);

        inspector.inspect(databaseFile);

        assertThat(Files.readAllBytes(databaseFile)).isEqualTo(before);
    }

    @Test
    void rejectsDatabaseWithoutEmberTables() throws SQLException {
        Path databaseFile = directory.resolve("foreign.emm");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE movie (id INTEGER)");
        }

        assertThatThrownBy(() -> inspector.inspect(databaseFile))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("keine gültige Ember-Datenbank")
                .hasMessageEndingWith("sets, tvshow, episode, moviesource, tvshowsource.");
    }

    @Test
    void rejectsFileThatIsNoSqliteDatabase() throws IOException {
        Path databaseFile = Files.writeString(directory.resolve("broken.emm"), "this is not a database");

        assertThatThrownBy(() -> inspector.inspect(databaseFile))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("kann nicht gelesen werden");
    }

    @Test
    void doesNotCreateMissingDatabase() {
        Path databaseFile = directory.resolve("missing.emm");

        assertThatThrownBy(() -> inspector.inspect(databaseFile))
                .isInstanceOf(EmberProfileException.class);
        assertThat(databaseFile).doesNotExist();
    }
}
