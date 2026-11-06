package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.job.ParallelReads;
import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.scanner.TvShowCandidate;
import de.tiktak.kmm.scanner.TvShowDirectoryScanner;
import lombok.RequiredArgsConstructor;

/**
 * Reads a TV show folder completely: episodes, NFO timestamps and the content of all NFOs that
 * changed since the last scan. Runs outside the database transaction and for several
 * TV shows at the same time.
 */
@Component
@RequiredArgsConstructor
class TvShowFolderReader {

    /** Episode NFOs read concurrently per TV show, so that large shows do not hold up the scan. */
    static final int PARALLEL_EPISODES = 4;

    private final TvShowNfoFiles nfoFiles;

    /**
     * @return the TV show, or empty if the folder contains no video files
     */
    Optional<ScannedTvShow> read(TvShowDirectoryScanner scanner, Path showDirectory, KnownNfos known) {
        return scanner.scanShow(showDirectory).map(candidate -> withNfos(candidate, known));
    }

    private ScannedTvShow withNfos(TvShowCandidate candidate, KnownNfos known) {
        Optional<TvNfoState> showNfo = nfoFiles.findShowNfo(candidate.directory());
        Optional<LoadedNfo<TvShowNfo>> loadedShowNfo = showNfo
                .filter(nfo -> !known.isCurrentShowNfo(candidate.directory().toString(), nfo))
                .map(nfoFiles::loadShow);
        Map<Path, TvNfoState> episodeNfos = new HashMap<>();
        Map<Path, LoadedNfo<List<EpisodeNfo>>> loadedEpisodeNfos = new HashMap<>();
        List<Optional<EpisodeNfos>> episodes = ParallelReads.map(candidate.episodes(), PARALLEL_EPISODES,
                "tv-scan-nfo-", episode -> readEpisodeNfo(episode.file(), known));
        episodes.stream().flatMap(Optional::stream).forEach(episode -> {
            episodeNfos.put(episode.file(), episode.nfo());
            episode.loaded().ifPresent(loaded -> loadedEpisodeNfos.put(episode.file(), loaded));
        });
        return new ScannedTvShow(candidate, showNfo, episodeNfos, loadedShowNfo, loadedEpisodeNfos);
    }

    private Optional<EpisodeNfos> readEpisodeNfo(Path episodeFile, KnownNfos known) {
        return nfoFiles.findEpisodeNfo(episodeFile).map(nfo -> new EpisodeNfos(episodeFile, nfo,
                known.isCurrentEpisodeNfo(episodeFile.toString(), nfo) ? Optional.empty()
                        : Optional.of(nfoFiles.loadEpisodes(nfo))));
    }

    private record EpisodeNfos(Path file, TvNfoState nfo, Optional<LoadedNfo<List<EpisodeNfo>>> loaded) {
    }
}
