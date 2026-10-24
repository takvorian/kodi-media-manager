package de.tiktak.kmm.migration.ember;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * Erzeugt Ember-Profile für Tests aus den Fixtures unter {@code src/test/resources/ember}.
 */
public final class EmberFixtures {

    public static final String DATABASE_FILE_NAME = "MyVideos48.emm";
    public static final long MOVIE_COUNT = 3;
    public static final long MOVIE_SET_COUNT = 1;
    public static final long TV_SHOW_COUNT = 1;
    public static final long EPISODE_COUNT = 2;
    public static final long MOVIE_SOURCE_COUNT = 1;
    public static final long TV_SHOW_SOURCE_COUNT = 1;

    private static final String FIXTURE_SQL = "/ember/MyVideos48-fixture.sql";
    private static final String FIXTURE_SETTINGS = "/ember/Settings.xml";
    private static final String STATEMENT_SEPARATOR = ";";
    private static final String SQL_COMMENT_PREFIX = "--";

    private EmberFixtures() {
    }

    public static Path createProfile(Path directory) {
        createDatabase(directory, DATABASE_FILE_NAME);
        copySettings(directory);
        return directory;
    }

    public static Path createDatabase(Path directory, String fileName) {
        Path databaseFile = directory.resolve(fileName);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement()) {
            for (String sql : fixtureStatements()) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Fixture database could not be created", e);
        }
        return databaseFile;
    }

    public static Path copySettings(Path directory) {
        Path target = directory.resolve(EmberProfileDetector.SETTINGS_FILE_NAME);
        try (InputStream input = EmberFixtures.class.getResourceAsStream(FIXTURE_SETTINGS)) {
            Files.copy(input, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target;
    }

    private static Iterable<String> fixtureStatements() {
        try (InputStream input = EmberFixtures.class.getResourceAsStream(FIXTURE_SQL)) {
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(script.split(STATEMENT_SEPARATOR))
                    .map(EmberFixtures::stripComments)
                    .filter(sql -> !sql.isBlank())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripComments(String sql) {
        return sql.lines()
                .filter(line -> !line.strip().startsWith(SQL_COMMENT_PREFIX))
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
