package de.tiktak.kmm.backup;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of an import.
 *
 * @param dryRun          whether the import was rolled back
 * @param mode            kind of import
 * @param results         numbers per table; dependent lists count towards their entries
 * @param unmappedPaths   paths no rule of the mapping matched; they stayed unchanged
 * @param credentialsKept whether existing credentials were kept because the file contains none
 * @param safetyCopy      file with the state before the import, {@code null} for a dry run
 * @param warnings        notes for the user
 */
public record BackupImportReport(boolean dryRun, BackupImportRequest.Mode mode, List<TableResult> results,
        int unmappedPaths, boolean credentialsKept, String safetyCopy, List<LocalizedText> warnings) {

    public record TableResult(String table, int created, int updated, int skipped) {
    }
}
