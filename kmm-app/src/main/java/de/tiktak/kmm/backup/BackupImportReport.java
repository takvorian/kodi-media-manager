package de.tiktak.kmm.backup;

import java.util.List;

/**
 * Ergebnis eines Imports.
 *
 * @param dryRun          ob der Import zurückgerollt wurde
 * @param mode            Art des Imports
 * @param results         Zahlen je Tabelle, abhängige Listen zählen zu ihren Einträgen
 * @param unmappedPaths   Pfade, auf die keine Regel der Zuordnung passte; sie blieben unverändert
 * @param credentialsKept ob vorhandene Zugangsdaten erhalten blieben, weil die Datei keine enthält
 * @param safetyCopy      Datei mit dem Stand vor dem Import, bei einem Probelauf {@code null}
 * @param warnings        Hinweise für den Benutzer
 */
public record BackupImportReport(boolean dryRun, BackupImportRequest.Mode mode, List<TableResult> results,
        int unmappedPaths, boolean credentialsKept, String safetyCopy, List<String> warnings) {

    public record TableResult(String table, int created, int updated, int skipped) {
    }
}
