package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.scanner.TvShowCandidate;

/**
 * A TV show found, with the state of its NFOs, read outside the database transaction. NFOs
 * that have changed since the last scan have already been read.
 *
 * @param showNfo           the {@code tvshow.nfo}, if present
 * @param episodeNfos       NFO per episode file; files without an NFO are missing
 * @param loadedShowNfo     content of the {@code tvshow.nfo}, if it was read
 * @param loadedEpisodeNfos content of the episode NFOs read, per episode file
 */
record ScannedTvShow(TvShowCandidate candidate, Optional<TvNfoState> showNfo, Map<Path, TvNfoState> episodeNfos,
                     Optional<LoadedNfo<TvShowNfo>> loadedShowNfo,
                     Map<Path, LoadedNfo<List<EpisodeNfo>>> loadedEpisodeNfos) {

    ScannedTvShow {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(showNfo, "showNfo must not be null");
        Objects.requireNonNull(loadedShowNfo, "loadedShowNfo must not be null");
        episodeNfos = Map.copyOf(episodeNfos);
        loadedEpisodeNfos = Map.copyOf(loadedEpisodeNfos);
    }

    String path() {
        return candidate.directory().toString();
    }

    /**
     * @return the previously read content of the {@code tvshow.nfo}, or empty if it was not read or was read in a
     *         different state
     */
    Optional<LoadedNfo<TvShowNfo>> loadedShowNfo(TvNfoState nfo) {
        return loadedShowNfo.filter(loaded -> loaded.isOf(nfo));
    }

    /**
     * @return the previously read content of the episode NFO, or empty if it was not read or was read in a
     *         different state
     */
    Optional<LoadedNfo<List<EpisodeNfo>>> loadedEpisodeNfo(Path episodeFile, TvNfoState nfo) {
        return Optional.ofNullable(loadedEpisodeNfos.get(episodeFile)).filter(loaded -> loaded.isOf(nfo));
    }
}
