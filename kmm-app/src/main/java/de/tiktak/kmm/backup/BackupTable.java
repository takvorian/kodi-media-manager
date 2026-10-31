package de.tiktak.kmm.backup;

import java.util.List;
import java.util.Map;

/**
 * Beschreibt eine Tabelle für die Sicherung.
 *
 * @param name       Name der Tabelle
 * @param idColumn   Spalte mit der erzeugten ID oder {@code null}
 * @param keyColumns Spalten, an denen ein Eintrag beim Zusammenführen wiedererkannt wird; leer bei abhängigen Listen,
 *                   die mit ihrem Eltern-Eintrag ersetzt werden
 * @param parents    Fremdschlüssel-Spalten mit der Tabelle, auf die sie verweisen
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
