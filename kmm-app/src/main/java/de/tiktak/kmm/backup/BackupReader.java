package de.tiktak.kmm.backup;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import de.tiktak.kmm.i18n.LocalizedText;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads backup files as a stream so that large libraries need not fit into memory. It skips tables that KMM does not
 * know: their names thus never get into SQL.
 */
@Component
@RequiredArgsConstructor
class BackupReader {

    private static final Set<String> KNOWN_TABLES =
            BackupTables.ALL.stream().map(BackupTable::name).collect(Collectors.toSet());

    private final ObjectMapper objectMapper;

    /**
     * @throws BackupFileException if the file is damaged
     */
    BackupHeader readHeader(Path file) {
        List<BackupHeader.Source> sources = new ArrayList<>();
        Parsed parsed = parse(file, BackupTables.MEDIA_SOURCE, (table, row) -> sources.add(new BackupHeader.Source(
                String.valueOf(row.get("name")), String.valueOf(row.get("path")),
                String.valueOf(row.get("media_type")))));
        return parsed.header(sources);
    }

    /**
     * Delivers all rows in the order of the file.
     *
     * @throws BackupFileException if the file is damaged
     */
    void readRows(Path file, BiConsumer<String, Map<String, Object>> rows) {
        parse(file, null, rows);
    }

    /**
     * @param lastTable table after which reading ends; {@code null} for all
     */
    private Parsed parse(Path file, String lastTable, BiConsumer<String, Map<String, Object>> rows) {
        try (InputStream input = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)));
                JsonParser parser = objectMapper.createParser(input)) {
            require(parser.nextToken() == JsonToken.START_OBJECT);
            Parsed parsed = new Parsed();
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if (BackupFormat.TABLES.equals(field)) {
                    parseTables(parser, lastTable, rows);
                    if (lastTable != null) {
                        return parsed;
                    }
                } else {
                    parsed.put(field, tree(parser));
                }
            }
            return parsed;
        } catch (IOException | JacksonException e) {
            throw BackupFileException.invalid(e);
        }
    }

    private void parseTables(JsonParser parser, String lastTable, BiConsumer<String, Map<String, Object>> rows) {
        require(parser.currentToken() == JsonToken.START_OBJECT);
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            String table = parser.currentName();
            require(parser.nextToken() == JsonToken.START_ARRAY);
            if (KNOWN_TABLES.contains(table)) {
                parseRows(parser, table, rows);
            } else {
                parser.skipChildren();
            }
            if (table.equals(lastTable)) {
                return;
            }
        }
    }

    private void parseRows(JsonParser parser, String table, BiConsumer<String, Map<String, Object>> rows) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(parser.currentToken() == JsonToken.START_OBJECT);
            rows.accept(table, row(tree(parser)));
        }
    }

    /** Reads only the current value; the file continues behind it. */
    private JsonNode tree(JsonParser parser) {
        return objectMapper.readerFor(JsonNode.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(parser);
    }

    private static Map<String, Object> row(JsonNode node) {
        Map<String, Object> row = new LinkedHashMap<>();
        node.properties().forEach(column -> row.put(column.getKey(), value(column.getValue())));
        return row;
    }

    private static Object value(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        require(node.isString());
        return node.asString();
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw BackupFileException.invalid(null);
        }
    }

    /** Header of the file, as far as read. */
    private static final class Parsed {

        private final Map<String, JsonNode> fields = new LinkedHashMap<>();

        void put(String field, JsonNode value) {
            fields.put(field, value);
        }

        BackupHeader header(List<BackupHeader.Source> sources) {
            JsonNode format = fields.get(BackupFormat.FORMAT);
            JsonNode schema = fields.get(BackupFormat.SCHEMA_VERSION);
            JsonNode counts = fields.get(BackupFormat.COUNTS);
            require(format != null && format.isNumber() && schema != null && counts != null && counts.isObject());
            if (format.asInt() > BackupFormat.VERSION) {
                throw new BackupFileException(LocalizedText.of("backup.newerVersion"));
            }
            try {
                MigrationVersion.fromVersion(schema.asString());
            } catch (RuntimeException e) {
                throw BackupFileException.invalid(e);
            }
            Map<String, Integer> tableCounts = new LinkedHashMap<>();
            counts.properties().forEach(entry -> tableCounts.put(entry.getKey(), entry.getValue().asInt()));
            return new BackupHeader(schema.asString(), text(BackupFormat.APPLICATION_VERSION),
                    text(BackupFormat.CREATED_AT), flag(BackupFormat.CREDENTIALS), tableCounts, List.copyOf(sources));
        }

        private String text(String field) {
            JsonNode value = fields.get(field);
            return value == null ? null : value.asString();
        }

        private boolean flag(String field) {
            JsonNode value = fields.get(field);
            return value != null && value.asBoolean();
        }
    }
}
