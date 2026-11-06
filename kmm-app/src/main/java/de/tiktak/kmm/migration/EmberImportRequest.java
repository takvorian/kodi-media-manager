package de.tiktak.kmm.migration;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request for the Ember import.
 *
 * @param pathMappings    mapping of the Ember paths to paths in KMM; without a mapping paths stay unchanged
 * @param importSettings  whether Ember's settings are imported
 * @param importKodiHosts whether Ember's Kodi devices are imported including credentials
 * @param importTvShows   whether Ember's TV shows and episodes are imported
 * @param dryRun          dry run: check and report everything, but save nothing
 */
public record EmberImportRequest(
        @NotNull @Size(max = MAX_MAPPINGS) List<@Valid PathMapping> pathMappings,
        boolean importSettings,
        boolean importKodiHosts,
        boolean importTvShows,
        boolean dryRun) {

    static final int MAX_MAPPINGS = 50;

    public EmberImportRequest {
        pathMappings = pathMappings == null ? null : List.copyOf(pathMappings);
    }

    /**
     * @param emberPath path prefix as Ember stored it, e.g. {@code a:\medien\filme}
     * @param kmmPath   the same place as KMM sees it, e.g. {@code /media/filme}
     */
    public record PathMapping(@NotBlank String emberPath, @NotBlank String kmmPath) {
    }
}
