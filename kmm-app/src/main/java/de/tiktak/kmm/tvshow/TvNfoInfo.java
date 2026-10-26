package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Zustand einer NFO für die Detailansicht.
 *
 * @param path       vorhandene NFO bzw. Pfad, unter dem KMM sie anlegen würde
 * @param exists     ob die Datei existiert
 * @param modifiedAt letzte Änderung oder {@code null}
 */
public record TvNfoInfo(String path, boolean exists, Instant modifiedAt) {

    static TvNfoInfo of(Path plannedFile, Optional<TvNfoState> state) {
        return state.map(nfo -> new TvNfoInfo(nfo.file().toString(), true, nfo.modifiedAt()))
                .orElseGet(() -> new TvNfoInfo(plannedFile.toString(), false, null));
    }
}
