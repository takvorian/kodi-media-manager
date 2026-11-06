package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * State of an NFO for the detail view.
 *
 * @param path       existing NFO, or the path under which KMM would create it
 * @param exists     whether the file exists
 * @param modifiedAt last modification or {@code null}
 */
public record TvNfoInfo(String path, boolean exists, Instant modifiedAt) {

    static TvNfoInfo of(Path plannedFile, Optional<TvNfoState> state) {
        return state.map(nfo -> new TvNfoInfo(nfo.file().toString(), true, nfo.modifiedAt()))
                .orElseGet(() -> new TvNfoInfo(plannedFile.toString(), false, null));
    }
}
