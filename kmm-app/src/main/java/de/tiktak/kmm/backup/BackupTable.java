package de.tiktak.kmm.backup;

import java.util.List;
import java.util.Map;

/**
 * Describes a table for the backup.
 *
 * @param name       name of the table
 * @param idColumn   column with the generated ID, or {@code null}
 * @param keyColumns columns by which an entry is recognized when merging; empty for dependent lists, which are
 *                   replaced together with their parent entry
 * @param parents    foreign key columns with the table they refer to
 */
record BackupTable(String name, String idColumn, List<String> keyColumns, Map<String, String> parents) {

    static BackupTable entity(String name, List<String> keyColumns, Map<String, String> parents) {
        return new BackupTable(name, "id", keyColumns, parents);
    }

    static BackupTable list(String name, String ownerColumn, String owner) {
        return new BackupTable(name, null, List.of(), Map.of(ownerColumn, owner));
    }

    boolean isList() {
        return keyColumns.isEmpty();
    }
}
