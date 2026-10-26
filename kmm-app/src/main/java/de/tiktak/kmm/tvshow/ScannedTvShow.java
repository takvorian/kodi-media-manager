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
 * Eine gefundene Serie mit dem Zustand ihrer NFOs, gelesen außerhalb der Datenbanktransaktion. NFOs,
 * die sich seit dem letzten Scan geändert haben, sind bereits eingelesen.
 *
 * @param showNfo           die {@code tvshow.nfo}, sofern vorhanden
 * @param episodeNfos       NFO je Episodendatei; Dateien ohne NFO fehlen
 * @param loadedShowNfo     Inhalt der {@code tvshow.nfo}, sofern sie gelesen wurde
 * @param loadedEpisodeNfos Inhalt der gelesenen Episoden-NFOs je Episodendatei
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
     * @return den vorab gelesenen Inhalt der {@code tvshow.nfo} oder leer, wenn sie nicht oder in einem
     *         anderen Zustand gelesen wurde
     */
    Optional<LoadedNfo<TvShowNfo>> loadedShowNfo(TvNfoState nfo) {
        return loadedShowNfo.filter(loaded -> loaded.isOf(nfo));
    }

    /**
     * @return den vorab gelesenen Inhalt der Episoden-NFO oder leer, wenn sie nicht oder in einem anderen
     *         Zustand gelesen wurde
     */
    Optional<LoadedNfo<List<EpisodeNfo>>> loadedEpisodeNfo(Path episodeFile, TvNfoState nfo) {
        return Optional.ofNullable(loadedEpisodeNfos.get(episodeFile)).filter(loaded -> loaded.isOf(nfo));
    }
}
