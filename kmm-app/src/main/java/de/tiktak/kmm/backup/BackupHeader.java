package de.tiktak.kmm.backup;

import java.util.List;
import java.util.Map;

/**
 * What a backup file contains, read without its rows.
 *
 * @param schemaVersion      state of the database migrations at export
 * @param applicationVersion KMM version at export
 * @param createdAt          time of the export
 * @param credentials        whether API keys and passwords are included
 * @param counts             number of rows per table
 * @param sources            sources of the file; their paths are the template for the path mapping
 */
public record BackupHeader(String schemaVersion, String applicationVersion, String createdAt, boolean credentials,
        Map<String, Integer> counts, List<Source> sources) {

    public record Source(String name, String path, String mediaType) {
    }
}
