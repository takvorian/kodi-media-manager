package de.tiktak.kmm.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Schreibt eine {@link ExportTable} als CSV, JSON oder HTML-Liste (ZIP mit {@code index.html} und Postern).
 */
@Component
@RequiredArgsConstructor
class ExportWriter {

    static final char CSV_SEPARATOR = ';';
    private static final char BYTE_ORDER_MARK = '﻿';
    private static final String LIST_SEPARATOR = ", ";
    private static final String POSTER_FOLDER = "posters/";

    private final ObjectMapper objectMapper;
    private final PosterThumbnails thumbnails;

    void write(ExportTable table, ExportFormat format, LocalDate exportedOn, OutputStream output) throws IOException {
        switch (format) {
            case CSV -> writeCsv(table, output);
            case JSON -> writeJson(table, output);
            case HTML -> writeHtml(table, exportedOn, output);
        }
    }

    /** Mit Byte-Order-Mark und Semikolon, damit Excel Umlaute und Spalten richtig erkennt; Zahlen mit Komma. */
    private static void writeCsv(ExportTable table, OutputStream output) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write(BYTE_ORDER_MARK);
        writeCsvLine(writer, table.columns().stream().map(ExportTable.Column::label).toList());
        for (ExportTable.Row row : table.rows()) {
            writeCsvLine(writer, row.values().stream().map(ExportWriter::text).toList());
        }
        writer.flush();
    }

    private static void writeCsvLine(Writer writer, List<String> cells) throws IOException {
        List<String> quoted = new ArrayList<>();
        for (String cell : cells) {
            boolean needsQuotes = cell.indexOf(CSV_SEPARATOR) >= 0 || cell.indexOf('"') >= 0
                    || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0;
            quoted.add(needsQuotes ? '"' + cell.replace("\"", "\"\"") + '"' : cell);
        }
        writer.write(String.join(String.valueOf(CSV_SEPARATOR), quoted));
        writer.write("\r\n");
    }

    private void writeJson(ExportTable table, OutputStream output) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ExportTable.Row row : table.rows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            for (int index = 0; index < table.columns().size(); index++) {
                Object value = row.values().get(index);
                entry.put(table.columns().get(index).key(), value instanceof LocalDate date ? date.toString() : value);
            }
            entries.add(entry);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, entries);
    }

    private void writeHtml(ExportTable table, LocalDate exportedOn, OutputStream output) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        Map<Long, String> posters = new LinkedHashMap<>();
        for (ExportTable.Row row : table.rows()) {
            if (row.poster() == null) {
                continue;
            }
            Optional<byte[]> thumbnail = thumbnails.thumbnail(row.poster());
            if (thumbnail.isPresent()) {
                String name = POSTER_FOLDER + row.id() + ".jpg";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(thumbnail.get());
                zip.closeEntry();
                posters.put(row.id(), name);
            }
        }
        zip.putNextEntry(new ZipEntry("index.html"));
        zip.write(HtmlPage.render(table, posters, exportedOn).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.finish();
    }

    /** Text eines Werts; Listen mit Komma verbunden, {@code null} leer. */
    static String text(Object value) {
        return switch (value) {
            case null -> "";
            case List<?> list -> String.join(LIST_SEPARATOR, list.stream().map(String::valueOf).toList());
            case Boolean bool -> bool ? "ja" : "nein";
            case Double number -> String.format(Locale.GERMANY, "%.1f", number);
            default -> String.valueOf(value);
        };
    }
}
