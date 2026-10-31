package de.tiktak.kmm.backup;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param mode         was mit den vorhandenen Daten geschieht
 * @param pathMappings Pfad-Zuordnung, leer, wenn die Pfade gleich bleiben
 * @param dryRun       ob der Import am Ende zurückgerollt wird
 */
public record BackupImportRequest(
        @NotNull Mode mode,
        @NotNull @Size(max = MAX_MAPPINGS) List<@Valid PathMapping> pathMappings,
        boolean dryRun) {

    static final int MAX_MAPPINGS = 100;

    public enum Mode {
        /** Die Datenbank hat danach den Stand der Datei. */
        REPLACE,
        /** Vorhandenes bleibt; bei gleichen Einträgen gewinnt die Datei. */
        MERGE
    }

    public record PathMapping(@NotBlank String from, @NotBlank String to) {
    }
}
