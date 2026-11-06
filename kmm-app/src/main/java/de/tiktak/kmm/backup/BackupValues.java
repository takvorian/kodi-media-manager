package de.tiktak.kmm.backup;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.TimeZone;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Passes values from the file according to the column type of the target database.
 */
final class BackupValues {

    private static final String UTC = "UTC";

    private BackupValues() {
    }

    /**
     * @throws BackupFileException if the value does not fit the column type
     */
    static void bind(PreparedStatement statement, int index, Object value, int type) throws SQLException {
        if (value == null) {
            statement.setNull(index, type);
            return;
        }
        try {
            switch (type) {
                case Types.BOOLEAN, Types.BIT -> statement.setBoolean(index, booleanOf(value));
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> statement.setInt(index, integerOf(value));
                case Types.BIGINT -> statement.setLong(index, ((Number) value).longValue());
                case Types.REAL, Types.FLOAT, Types.DOUBLE ->
                        statement.setDouble(index, ((Number) value).doubleValue());
                case Types.DECIMAL, Types.NUMERIC -> statement.setBigDecimal(index, new BigDecimal(value.toString()));
                case Types.DATE -> statement.setObject(index, LocalDate.parse((String) value));
                case Types.TIMESTAMP_WITH_TIMEZONE -> statement.setObject(index,
                        Instant.parse((String) value).atOffset(ZoneOffset.UTC));
                case Types.TIMESTAMP -> statement.setTimestamp(index, Timestamp.from(Instant.parse((String) value)),
                        Calendar.getInstance(TimeZone.getTimeZone(UTC)));
                default -> statement.setString(index, value.toString());
            }
        } catch (ClassCastException | NumberFormatException | DateTimeParseException e) {
            throw new BackupFileException(LocalizedText.of("backup.invalidValue").with("value", value), e);
        }
    }

    /** Depending on the driver, MariaDB may also report {@code BOOLEAN} as a number. */
    private static boolean booleanOf(Object value) {
        return value instanceof Number number ? number.intValue() != 0 : (Boolean) value;
    }

    private static int integerOf(Object value) {
        return value instanceof Boolean flag ? (flag ? 1 : 0) : ((Number) value).intValue();
    }
}
