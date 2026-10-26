package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.scanner.TvShowCandidate;

/**
 * Eine gefundene Serie mit dem Zustand ihrer NFOs, gelesen außerhalb der Datenbanktransaktion.
 *
 * @param showNfo     die {@code tvshow.nfo}, sofern vorhanden
 * @param episodeNfos NFO je Episodendatei; Dateien ohne NFO fehlen
 */
record ScannedTvShow(TvShowCandidate candidate, Optional<TvNfoState> showNfo, Map<Path, TvNfoState> episodeNfos) {

    ScannedTvShow {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(showNfo, "showNfo must not be null");
        episodeNfos = Map.copyOf(episodeNfos);
    }

    String path() {
        return candidate.directory().toString();
    }
}
