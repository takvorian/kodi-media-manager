package de.tiktak.kmm.backup;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param mode         what happens to the existing data
 * @param pathMappings path mapping, empty if the paths stay the same
 * @param dryRun       whether the import is rolled back at the end
 */
public record BackupImportRequest(
        @NotNull Mode mode,
        @NotNull @Size(max = MAX_MAPPINGS) List<@Valid PathMapping> pathMappings,
        boolean dryRun) {

    static final int MAX_MAPPINGS = 100;

    public enum Mode {
        /** Afterwards the database has the state of the file. */
        REPLACE,
        /** Existing data stays; for equal entries the file wins. */
        MERGE
    }

    public record PathMapping(@NotBlank String from, @NotBlank String to) {
    }
}
