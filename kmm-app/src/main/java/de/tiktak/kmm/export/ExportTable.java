package de.tiktak.kmm.export;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exportierte Einträge als Tabelle: Spaltennamen, Zeilen und je Zeile das Poster, falls vorhanden.
 *
 * @param title Überschrift der HTML-Liste, z. B. „Filme“
 */
record ExportTable(String title, List<Column> columns, List<Row> rows) {

    ExportTable {
        Objects.requireNonNull(title, "title must not be null");
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    /**
     * @param key   Name im JSON, z. B. {@code originalTitle}
     * @param label Spaltenkopf in CSV und HTML, z. B. „Originaltitel“
     */
    record Column(String key, String label) {
    }

    /**
     * @param id     ID in KMM; benennt das Poster im ZIP
     * @param values Werte in der Reihenfolge der Spalten; Listen werden mit Komma verbunden, {@code null} bleibt leer
     * @param poster Poster-Datei oder {@code null}
     */
    record Row(long id, List<Object> values, Path poster) {

        Row {
            values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
}
