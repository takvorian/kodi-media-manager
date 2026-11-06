package de.tiktak.kmm.migration.ember.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads column values from the Ember database. Ember stores many numbers as text and empty values as empty
 * strings; both are normalized here. Unusable values count as not set.
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

    /** First whole number in the text, e.g. {@code 120} from "120 min". */
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

    /**
     * Ember stores points in time as seconds since 1970, but converts the local time without a time zone
     * ({@code ConvertToUnixTimestamp}). The value is therefore a time without zone.
     */
    static LocalDateTime localDateTime(ResultSet row, String column) throws SQLException {
        long seconds = row.getLong(column);
        return row.wasNull() || seconds <= NO_TIMESTAMP ? null
                : LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC);
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
