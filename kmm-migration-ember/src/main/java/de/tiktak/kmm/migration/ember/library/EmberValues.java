package de.tiktak.kmm.migration.ember.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest Spaltenwerte aus der Ember-Datenbank. Ember speichert viele Zahlen als Text und leere Werte
 * als Leerstring; beides wird hier vereinheitlicht. Unbrauchbare Werte gelten als nicht gesetzt.
 */
final class EmberValues {

    private static final Pattern NUMBER = Pattern.compile("-?[0-9]+");
    private static final int DATE_LENGTH = 10;
    private static final long NO_TIMESTAMP = 0;

    private EmberValues() {
    }

    static String text(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** Erste ganze Zahl im Text, z. B. {@code 120} aus „120 min“. */
    static Integer integer(ResultSet row, String column) throws SQLException {
        String value = text(row, column);
        if (value == null) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(value);
        try {
            return matcher.find() ? Integer.valueOf(matcher.group()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer positiveInteger(ResultSet row, String column) throws SQLException {
        Integer value = integer(row, column);
        return value != null && value > 0 ? value : null;
    }

    /** Ember speichert Zeitpunkte als Unix-Zeit in Sekunden. */
    static Instant epochSeconds(ResultSet row, String column) throws SQLException {
        long seconds = row.getLong(column);
        return row.wasNull() || seconds <= NO_TIMESTAMP ? null : Instant.ofEpochSecond(seconds);
    }

    static LocalDate date(ResultSet row, String column) throws SQLException {
        String value = text(row, column);
        if (value == null || value.length() < DATE_LENGTH) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, DATE_LENGTH));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Integer nullableInt(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }
}
