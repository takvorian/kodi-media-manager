package de.tiktak.kmm.backup;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.movie.FilePathHash;
import de.tiktak.kmm.settings.CredentialSettings;
import de.tiktak.kmm.settings.PathSettings;

/**
 * A single import run. Replacing first deletes everything and then merges into the empty database; so there is only
 * one import path. The database assigns new IDs, the foreign keys are rewritten. Runs in the caller's transaction.
 */
final class BackupImportRun {

    private static final int BATCH_SIZE = 500;
    private static final LocalizedText PROGRESS_MESSAGE = LocalizedText.of("backup.import.progress");
    private static final String NAME_COLUMN = "name";
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}");
    private static final String PATH_COLUMN = "path";
    private static final String SEPARATOR = "/";
    private static final char WINDOWS_SEPARATOR = '\\';
    private static final Map<String, Map<String, String>> PATH_COLUMNS = Map.of(
            BackupTables.MOVIE, Map.of("file_path", "file_path_hash"),
            BackupTables.TV_SHOW, Map.of("directory_path", "directory_path_hash"),
            BackupTables.EPISODE, Map.of("file_path", "file_path_hash"));

    private final JdbcTemplate jdbc;
    private final BackupHeader header;
    private final BackupImportRequest request;
    private final JobContext context;
    private final BackupPaths paths;
    private final Map<String, BackupTable> tables = BackupTables.ALL.stream()
            .collect(Collectors.toMap(BackupTable::name, table -> table));
    private final Map<String, Map<String, Integer>> columnTypes = new HashMap<>();
    private final Map<String, Map<Long, Long>> newIds = new HashMap<>();
    private final Map<String, Map<List<String>, Long>> existingKeys = new HashMap<>();
    private final Map<String, int[]> counters = new LinkedHashMap<>();
    private final List<ExistingSource> sources = new ArrayList<>();
    private final List<LocalizedText> warnings = new ArrayList<>();
    private final Map<String, String> keptSettings = new LinkedHashMap<>();
    private final Map<String, String> keptKodiPasswords = new HashMap<>();
    private final List<Object[]> batch = new ArrayList<>();
    private String batchSql;
    private List<Integer> batchTypes;
    private int done;
    private final int total;

    BackupImportRun(JdbcTemplate jdbc, BackupHeader header, BackupImportRequest request, JobContext context) {
        this.jdbc = jdbc;
        this.header = header;
        this.request = request;
        this.context = context;
        this.paths = BackupPaths.of(request.pathMappings());
        this.total = header.counts().values().stream().mapToInt(Integer::intValue).sum();
        BackupTables.ALL.stream().filter(table -> !table.isList())
                .forEach(table -> counters.put(table.name(), new int[3]));
    }

    void prepare() {
        if (request.mode() == BackupImportRequest.Mode.REPLACE) {
            if (!header.credentials()) {
                keepCredentials();
            }
            for (int index = BackupTables.ALL.size() - 1; index >= 0; index--) {
                jdbc.update("DELETE FROM " + BackupTables.ALL.get(index).name());
            }
        }
        BackupTables.ALL.stream().filter(table -> !table.isList()).forEach(this::loadExistingKeys);
        jdbc.query("SELECT name, path FROM " + BackupTables.MEDIA_SOURCE, row -> {
            sources.add(new ExistingSource(row.getString(1), row.getString(2)));
        });
    }

    /** Processes one row of the file. */
    void accept(String tableName, Map<String, Object> fileRow) {
        context.throwIfCancellationRequested();
        if (done++ % BATCH_SIZE == 0) {
            context.reportProgress(done, total, PROGRESS_MESSAGE);
        }
        BackupTable table = tables.get(tableName);
        Map<String, Object> row = withKnownColumns(table, fileRow);
        Object fileId = table.idColumn() == null ? null : row.remove(table.idColumn());
        if (!remapParents(table, row)) {
            skip(table);
            return;
        }
        mapPaths(table, row);
        if (table.isList()) {
            insertInBatch(table, row);
            return;
        }
        flushBatch();
        importEntry(table, row, fileId);
    }

    BackupImportReport finish(String safetyCopy) {
        flushBatch();
        restoreKeptSettings();
        List<BackupImportReport.TableResult> results = counters.entrySet().stream()
                .map(entry -> new BackupImportReport.TableResult(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2]))
                .toList();
        return new BackupImportReport(request.dryRun(), request.mode(), results, paths.unmapped(),
                !header.credentials(), safetyCopy, List.copyOf(warnings));
    }

    private void keepCredentials() {
        jdbc.query("SELECT * FROM " + BackupTables.APP_SETTING, row -> {
            String key = row.getString(BackupTables.SETTING_KEY);
            if (CredentialSettings.isCredential(key)) {
                keptSettings.put(key, row.getString("setting_value"));
            }
        });
        jdbc.query("SELECT name, password FROM " + BackupTables.KODI_HOST, row -> {
            keptKodiPasswords.put(keyPart(NAME_COLUMN, row.getString(1)), row.getString(2));
        });
    }

    private void restoreKeptSettings() {
        keptSettings.forEach((key, value) -> jdbc.update("INSERT INTO %s (%s, setting_value) VALUES (?, ?)"
                .formatted(BackupTables.APP_SETTING, BackupTables.SETTING_KEY), key, value));
    }

    private void loadExistingKeys(BackupTable table) {
        Map<List<String>, Long> keys = new HashMap<>();
        String columns = String.join(", ", table.keyColumns());
        String id = table.idColumn() == null ? "" : ", " + table.idColumn();
        jdbc.query("SELECT %s%s FROM %s".formatted(columns, id, table.name()), row -> {
            List<String> key = new ArrayList<>();
            for (int index = 0; index < table.keyColumns().size(); index++) {
                key.add(keyPart(table.keyColumns().get(index), row.getString(index + 1)));
            }
            keys.put(key, table.idColumn() == null ? null : row.getLong(table.keyColumns().size() + 1));
        });
        existingKeys.put(table.name(), keys);
    }

    /**
     * MariaDB treats names as equal regardless of case and accents. Here this applies to all databases, otherwise the
     * move from H2 fails on two movie sets that differ only in that.
     */
    private static String keyPart(String column, Object value) {
        String text = String.valueOf(value);
        return NAME_COLUMN.equals(column) ? comparableName(text) : text;
    }

    private static String comparableName(String name) {
        String withoutMarks = COMBINING_MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return withoutMarks.toLowerCase(Locale.ROOT).replace("ß", "ss").strip();
    }

    /** Only columns known to the schema get into SQL; whatever a newer or foreign file brings along is dropped. */
    private Map<String, Object> withKnownColumns(BackupTable table, Map<String, Object> fileRow) {
        Map<String, Integer> types = columnTypes(table);
        Map<String, Object> row = new LinkedHashMap<>();
        fileRow.forEach((column, value) -> {
            if (types.containsKey(column)) {
                row.put(column, value);
            }
        });
        return row;
    }

    private Map<String, Integer> columnTypes(BackupTable table) {
        return columnTypes.computeIfAbsent(table.name(), name -> jdbc.query("SELECT * FROM " + name + " WHERE 1 = 0",
                (ResultSet result) -> {
                    ResultSetMetaData metaData = result.getMetaData();
                    Map<String, Integer> types = new HashMap<>();
                    for (int column = 1; column <= metaData.getColumnCount(); column++) {
                        types.put(metaData.getColumnLabel(column).toLowerCase(Locale.ROOT),
                                metaData.getColumnType(column));
                    }
                    return types;
                }));
    }

    /**
     * @return {@code false} if the parent entry was not imported
     */
    private boolean remapParents(BackupTable table, Map<String, Object> row) {
        for (Map.Entry<String, String> parent : table.parents().entrySet()) {
            Object fileId = row.get(parent.getKey());
            if (fileId != null) {
                Long newId = newIds.getOrDefault(parent.getValue(), Map.of()).get(idOf(fileId));
                if (newId == null) {
                    return false;
                }
                row.put(parent.getKey(), newId);
            }
        }
        return true;
    }

    private static long idOf(Object fileId) {
        if (fileId instanceof Number number) {
            return number.longValue();
        }
        throw new BackupFileException(LocalizedText.of("backup.invalidId").with("id", String.valueOf(fileId)));
    }

    private void mapPaths(BackupTable table, Map<String, Object> row) {
        switch (table.name()) {
            case BackupTables.MEDIA_SOURCE -> mapColumn(row, PATH_COLUMN);
            case BackupTables.KODI_HOST_PATH_MAPPING -> mapColumn(row, "local_path");
            case BackupTables.APP_SETTING -> row.computeIfPresent("setting_value", (column, value) ->
                    PathSettings.mapPaths(String.valueOf(row.get(BackupTables.SETTING_KEY)), value.toString(),
                            paths::map));
            default -> PATH_COLUMNS.getOrDefault(table.name(), Map.of()).forEach((pathColumn, hashColumn) -> {
                if (row.get(pathColumn) instanceof String path) {
                    String mapped = paths.map(path);
                    row.put(pathColumn, mapped);
                    row.put(hashColumn, FilePathHash.of(mapped));
                }
            });
        }
    }

    private void mapColumn(Map<String, Object> row, String column) {
        if (row.get(column) instanceof String path) {
            row.put(column, paths.map(path));
        }
    }

    private void importEntry(BackupTable table, Map<String, Object> row, Object fileId) {
        List<String> key = table.keyColumns().stream().map(column -> keyPart(column, row.get(column))).toList();
        Map<List<String>, Long> known = existingKeys.get(table.name());
        boolean exists = known.containsKey(key);
        if (BackupTables.MEDIA_SOURCE.equals(table.name()) && !exists && !acceptSource(row)) {
            skip(table);
            return;
        }
        if (BackupTables.KODI_HOST.equals(table.name())) {
            applyKeptKodiPassword(row, exists);
        }
        Long id = exists ? known.get(key) : null;
        if (exists) {
            update(table, row, key, id);
            counters.get(table.name())[1]++;
        } else {
            id = insert(table, row);
            known.put(key, id);
            counters.get(table.name())[0]++;
        }
        if (table.idColumn() != null) {
            newIds.computeIfAbsent(table.name(), name -> new HashMap<>()).put(idOf(fileId), id);
        }
    }

    /**
     * Same rule as when creating a source: unique name, directories do not overlap.
     *
     * @return whether the source is imported
     */
    private boolean acceptSource(Map<String, Object> row) {
        String name = String.valueOf(row.get(NAME_COLUMN));
        String path = String.valueOf(row.get(PATH_COLUMN));
        for (ExistingSource source : sources) {
            if (source.name().equalsIgnoreCase(name)) {
                warnings.add(sourceWarning("backup.warning.sourceNameTaken", name, path));
                return false;
            }
            if (source.overlaps(path)) {
                warnings.add(sourceWarning("backup.warning.sourceOverlaps", name, path).with("other", source.name()));
                return false;
            }
        }
        sources.add(new ExistingSource(name, path));
        warnIfUnreachable(name, path);
        return true;
    }

    private void warnIfUnreachable(String name, String path) {
        try {
            if (Files.isDirectory(Path.of(path))) {
                return;
            }
        } catch (InvalidPathException e) {
            warnings.add(sourceWarning("backup.warning.sourcePathInvalid", name, path));
            return;
        }
        warnings.add(sourceWarning("backup.warning.sourceUnreachable", name, path));
    }

    private static LocalizedText sourceWarning(String key, String name, String path) {
        return LocalizedText.of(key).with("name", name).with("path", path);
    }

    /** A file without credentials does not overwrite the existing passwords. */
    private void applyKeptKodiPassword(Map<String, Object> row, boolean exists) {
        if (header.credentials()) {
            return;
        }
        if (exists) {
            row.remove(BackupTables.KODI_PASSWORD);
        } else {
            row.put(BackupTables.KODI_PASSWORD, keptKodiPasswords.get(keyPart(NAME_COLUMN, row.get(NAME_COLUMN))));
        }
    }

    private long insert(BackupTable table, Map<String, Object> row) {
        Map<String, Integer> types = columnTypes(table);
        List<String> columns = List.copyOf(row.keySet());
        String sql = insertSql(table, columns);
        if (table.idColumn() == null) {
            jdbc.update(connection -> bound(connection.prepareStatement(sql), columns, row, types));
            return 0;
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> bound(connection.prepareStatement(sql, new String[] {table.idColumn()}), columns, row,
                types), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void update(BackupTable table, Map<String, Object> row, List<String> key, Long id) {
        Map<String, Integer> types = columnTypes(table);
        List<String> columns = row.keySet().stream().filter(column -> !table.keyColumns().contains(column)).toList();
        if (!columns.isEmpty()) {
            String assignments = columns.stream().map(column -> column + " = ?").collect(Collectors.joining(", "));
            String where = table.idColumn() != null ? table.idColumn() + " = ?"
                    : table.keyColumns().getFirst() + " = ?";
            Object target = table.idColumn() != null ? id : key.getFirst();
            jdbc.update(connection -> {
                PreparedStatement statement = bound(connection.prepareStatement(
                        "UPDATE %s SET %s WHERE %s".formatted(table.name(), assignments, where)), columns, row, types);
                statement.setObject(columns.size() + 1, target);
                return statement;
            });
        }
        if (table.idColumn() != null) {
            deleteLists(table, id);
        }
    }

    /** The file completely replaces the lists of an existing entry. */
    private void deleteLists(BackupTable table, long id) {
        BackupTables.ALL.stream().filter(BackupTable::isList).forEach(list -> list.parents().forEach((column, owner) -> {
            if (owner.equals(table.name())) {
                jdbc.update("DELETE FROM %s WHERE %s = ?".formatted(list.name(), column), id);
            }
        }));
    }

    private void insertInBatch(BackupTable table, Map<String, Object> row) {
        Map<String, Integer> types = columnTypes(table);
        List<String> columns = List.copyOf(row.keySet());
        String sql = insertSql(table, columns);
        if (!sql.equals(batchSql) || batch.size() >= BATCH_SIZE) {
            flushBatch();
            batchSql = sql;
            batchTypes = columns.stream().map(types::get).toList();
        }
        batch.add(columns.stream().map(row::get).toArray());
    }

    private void flushBatch() {
        if (batch.isEmpty()) {
            return;
        }
        List<Object[]> rows = List.copyOf(batch);
        List<Integer> types = batchTypes;
        jdbc.batchUpdate(batchSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                Object[] values = rows.get(index);
                for (int column = 0; column < values.length; column++) {
                    BackupValues.bind(statement, column + 1, values[column], types.get(column));
                }
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
        batch.clear();
    }

    private static String insertSql(BackupTable table, List<String> columns) {
        String placeholders = IntStream.range(0, columns.size()).mapToObj(index -> "?")
                .collect(Collectors.joining(", "));
        return "INSERT INTO %s (%s) VALUES (%s)".formatted(table.name(), String.join(", ", columns), placeholders);
    }

    private static PreparedStatement bound(PreparedStatement statement, List<String> columns, Map<String, Object> row,
            Map<String, Integer> types) throws SQLException {
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            BackupValues.bind(statement, index + 1, row.get(column), types.get(column));
        }
        return statement;
    }

    private void skip(BackupTable table) {
        if (!table.isList()) {
            counters.get(table.name())[2]++;
        }
    }

    /** Compares directories as text, because the paths of a file may belong to another system. */
    private record ExistingSource(String name, String path) {

        boolean overlaps(String other) {
            String mine = normalized(path);
            String theirs = normalized(other);
            return mine.equals(theirs) || mine.startsWith(theirs + SEPARATOR) || theirs.startsWith(mine + SEPARATOR);
        }

        private static String normalized(String directory) {
            String normalized = directory.replace(WINDOWS_SEPARATOR, SEPARATOR.charAt(0));
            return normalized.endsWith(SEPARATOR) ? normalized.substring(0, normalized.length() - 1) : normalized;
        }
    }
}
