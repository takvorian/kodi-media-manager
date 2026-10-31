package de.tiktak.kmm.backup;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import de.tiktak.kmm.settings.CredentialSettings;
import de.tiktak.kmm.system.ReleaseInfoService;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

/**
 * Schreibt alle Tabellen in eine Sicherungsdatei. Die Zeilen laufen über JDBC statt über die Entitäten, damit neue
 * Spalten ohne weiteres Zutun dabei sind; die Datei bleibt dabei zwischen den Datenbanken austauschbar.
 */
@Component
@RequiredArgsConstructor
class BackupWriter {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final Flyway flyway;
    private final ReleaseInfoService releaseInfoService;
    private final Clock clock;

    /**
     * @param credentials ob API-Keys und Passwörter in die Datei gelangen
     */
    void write(OutputStream target, boolean credentials) {
        try (JsonGenerator json = objectMapper.createGenerator(new GZIPOutputStream(target))) {
            json.writeStartObject();
            writeHeader(json, credentials);
            json.writeName(BackupFormat.TABLES);
            json.writeStartObject();
            for (BackupTable table : BackupTables.ALL) {
                json.writeName(table.name());
                json.writeStartArray();
                jdbcClient.sql("SELECT * FROM " + table.name())
                        .query((RowCallbackHandler) row -> writeRow(json, table, row, credentials));
                json.writeEndArray();
            }
            json.writeEndObject();
            json.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeHeader(JsonGenerator json, boolean credentials) {
        json.writeNumberProperty(BackupFormat.FORMAT, BackupFormat.VERSION);
        json.writeStringProperty(BackupFormat.SCHEMA_VERSION, flyway.info().current().getVersion().getVersion());
        json.writeStringProperty(BackupFormat.APPLICATION_VERSION, releaseInfoService.currentVersion());
        json.writeStringProperty(BackupFormat.CREATED_AT, clock.instant().toString());
        json.writeBooleanProperty(BackupFormat.CREDENTIALS, credentials);
        json.writeName(BackupFormat.COUNTS);
        json.writeStartObject();
        counts(credentials).forEach(json::writeNumberProperty);
        json.writeEndObject();
    }

    private Map<String, Integer> counts(boolean credentials) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BackupTable table : BackupTables.ALL) {
            counts.put(table.name(), BackupTables.APP_SETTING.equals(table.name()) && !credentials
                    ? settingsWithoutCredentials()
                    : jdbcClient.sql("SELECT COUNT(*) FROM " + table.name()).query(Integer.class).single());
        }
        return counts;
    }

    private int settingsWithoutCredentials() {
        return (int) jdbcClient.sql("SELECT %s FROM %s".formatted(BackupTables.SETTING_KEY, BackupTables.APP_SETTING))
                .query(String.class).stream()
                .filter(key -> !CredentialSettings.isCredential(key))
                .count();
    }

    private static void writeRow(JsonGenerator json, BackupTable table, ResultSet row, boolean credentials)
            throws SQLException {
        if (!credentials && BackupTables.APP_SETTING.equals(table.name())
                && CredentialSettings.isCredential(row.getString(BackupTables.SETTING_KEY))) {
            return;
        }
        ResultSetMetaData columns = row.getMetaData();
        json.writeStartObject();
        for (int column = 1; column <= columns.getColumnCount(); column++) {
            String name = columns.getColumnLabel(column).toLowerCase(Locale.ROOT);
            json.writeName(name);
            if (!credentials && BackupTables.KODI_HOST.equals(table.name())
                    && BackupTables.KODI_PASSWORD.equals(name)) {
                json.writeNull();
            } else {
                writeValue(json, row, column, columns.getColumnType(column));
            }
        }
        json.writeEndObject();
    }

    private static void writeValue(JsonGenerator json, ResultSet row, int column, int type) throws SQLException {
        Object value = row.getObject(column);
        if (value == null) {
            json.writeNull();
            return;
        }
        switch (type) {
            case Types.BOOLEAN, Types.BIT -> json.writeBoolean(row.getBoolean(column));
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> json.writeNumber(row.getLong(column));
            case Types.DECIMAL, Types.NUMERIC, Types.REAL, Types.FLOAT, Types.DOUBLE ->
                    json.writeNumber(row.getBigDecimal(column));
            case Types.DATE -> json.writeString(row.getObject(column, LocalDate.class).toString());
            case Types.TIMESTAMP_WITH_TIMEZONE ->
                    json.writeString(row.getObject(column, OffsetDateTime.class).toInstant().toString());
            case Types.TIMESTAMP -> json.writeString(row.getTimestamp(column, utc()).toInstant().toString());
            default -> json.writeString(row.getString(column));
        }
    }

    /** Spalten ohne Zeitzone wie {@code DATETIME} in MariaDB enthalten UTC, so schreibt sie auch Hibernate. */
    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
