package de.tiktak.kmm.backup;

/**
 * Field names and version of the file format: gzip-compressed JSON with the header, followed by the rows per table.
 */
final class BackupFormat {

    static final int VERSION = 1;
    static final String FORMAT = "format";
    static final String SCHEMA_VERSION = "schemaVersion";
    static final String APPLICATION_VERSION = "applicationVersion";
    static final String CREATED_AT = "createdAt";
    static final String CREDENTIALS = "credentials";
    static final String COUNTS = "counts";
    static final String TABLES = "tables";

    private BackupFormat() {
    }
}
