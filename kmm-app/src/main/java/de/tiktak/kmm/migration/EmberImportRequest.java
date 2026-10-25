package de.tiktak.kmm.migration;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Auftrag für den Ember-Import.
 *
 * @param profilePath    Ember-Profil- oder Installationsverzeichnis
 * @param pathMappings   Zuordnung der Ember-Pfade zu Pfaden in KMM; ohne Zuordnung bleiben Pfade unverändert
 * @param importSettings  ob Embers Einstellungen übernommen werden
 * @param importKodiHosts ob Embers Kodi-Geräte samt Zugangsdaten übernommen werden
 * @param dryRun         Probelauf: alles prüfen und berichten, aber nichts speichern
 */
public record EmberImportRequest(
        @NotBlank String profilePath,
        @NotNull @Size(max = MAX_MAPPINGS) List<@Valid PathMapping> pathMappings,
        boolean importSettings,
        boolean importKodiHosts,
        boolean dryRun) {

    static final int MAX_MAPPINGS = 50;

    public EmberImportRequest {
        pathMappings = pathMappings == null ? null : List.copyOf(pathMappings);
    }

    /**
     * @param emberPath Pfad-Präfix, wie Ember ihn gespeichert hat, z. B. {@code a:\medien\filme}
     * @param kmmPath   derselbe Ort aus Sicht von KMM, z. B. {@code /media/filme}
     */
    public record PathMapping(@NotBlank String emberPath, @NotBlank String kmmPath) {
    }
}
