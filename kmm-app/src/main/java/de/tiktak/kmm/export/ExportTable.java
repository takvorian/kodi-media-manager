package de.tiktak.kmm.export;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Exported entries as a table: column names, rows and the poster of each row, if any.
 *
 * @param title  heading of the HTML list, e.g. "Movies"
 * @param locale language of the texts and of numbers and dates in CSV and HTML
 */
record ExportTable(String title, Locale locale, List<Column> columns, List<Row> rows) {

    ExportTable {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(locale, "locale must not be null");
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    /**
     * @param key   name in JSON, e.g. {@code originalTitle}
     * @param label column heading in CSV and HTML, e.g. "Original title"
     */
    record Column(String key, String label) {
    }

    /**
     * @param id     ID in KMM; names the poster in the ZIP
     * @param values values in the order of the columns; lists are joined with commas, {@code null} stays empty
     * @param poster poster file or {@code null}
     */
    record Row(long id, List<Object> values, Path poster) {

        Row {
            values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
}
