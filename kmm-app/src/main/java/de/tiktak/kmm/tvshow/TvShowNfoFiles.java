package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.EpisodeNfoReader;
import de.tiktak.kmm.nfo.EpisodeNfoWriter;
import de.tiktak.kmm.nfo.EpisodeStreamDetails;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.PlaybackNfoWriter;
import de.tiktak.kmm.nfo.StreamDetailsNfoWriter;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.nfo.TvShowNfoReader;
import de.tiktak.kmm.nfo.TvShowNfoWriter;
import de.tiktak.kmm.scanner.TvShowFiles;
import lombok.extern.slf4j.Slf4j;

/**
 * Zugriff auf die NFOs von Serien ({@code tvshow.nfo}) und Episoden: finden, lesen und schreiben.
 */
@Slf4j
@Component
class TvShowNfoFiles {

    private final TvShowNfoReader showReader = new TvShowNfoReader();
    private final TvShowNfoWriter showWriter = new TvShowNfoWriter();
    private final EpisodeNfoReader episodeReader = new EpisodeNfoReader();
    private final EpisodeNfoWriter episodeWriter = new EpisodeNfoWriter();
    private final PlaybackNfoWriter playbackWriter = new PlaybackNfoWriter();
    private final StreamDetailsNfoWriter streamDetailsWriter = new StreamDetailsNfoWriter();

    Optional<TvNfoState> findShowNfo(Path showDirectory) {
        return state(TvShowFiles.tvShowNfo(showDirectory));
    }

    Optional<TvNfoState> findEpisodeNfo(Path episodeFile) {
        return state(TvShowFiles.episodeNfo(episodeFile));
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException wenn die Datei nicht gelesen werden kann
     */
    TvShowNfo readShow(TvNfoState nfo) {
        return showReader.read(nfo.file());
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException wenn die Datei nicht gelesen werden kann
     */
    List<EpisodeNfo> readEpisodes(TvNfoState nfo) {
        return episodeReader.read(nfo.file());
    }

    /** Liest die {@code tvshow.nfo}; eine unlesbare Datei wird protokolliert. */
    LoadedNfo<TvShowNfo> loadShow(TvNfoState nfo) {
        return load(nfo, () -> readShow(nfo));
    }

    /** Liest die NFO einer Episodendatei; eine unlesbare Datei wird protokolliert. */
    LoadedNfo<List<EpisodeNfo>> loadEpisodes(TvNfoState nfo) {
        return load(nfo, () -> readEpisodes(nfo));
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException wenn die Datei nicht geschrieben werden kann
     */
    TvNfoState writeShow(Path showDirectory, TvShowNfo nfo) {
        Path file = TvShowFiles.tvShowNfo(showDirectory);
        showWriter.write(file, nfo);
        return written(file);
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException wenn die Datei nicht geschrieben werden kann
     */
    TvNfoState writeEpisodes(Path episodeFile, List<EpisodeNfo> episodes) {
        Path file = TvShowFiles.episodeNfo(episodeFile);
        episodeWriter.write(file, episodes);
        return written(file);
    }

    /**
     * Schreibt nur den Wiedergabestand der Episode in ihre vorhandene NFO.
     *
     * @throws de.tiktak.kmm.nfo.NfoException wenn die NFO die Episode nicht enthält oder nicht geschrieben
     *                                        werden kann
     */
    TvNfoState writePlayback(TvNfoState nfo, int season, int episode, Integer playCount, LocalDateTime lastPlayed) {
        playbackWriter.writeEpisode(nfo.file(), season, episode, playCount, lastPlayed);
        return written(nfo.file());
    }

    /**
     * Ersetzt nur die technischen Angaben einer Episode in der vorhandenen NFO.
     *
     * @throws NfoException wenn die NFO die Episode nicht enthält oder nicht geschrieben werden kann
     */
    TvNfoState writeStreamDetails(TvNfoState nfo, List<EpisodeStreamDetails> episodes) {
        streamDetailsWriter.writeEpisodes(nfo.file(), episodes);
        return written(nfo.file());
    }

    private static <T> LoadedNfo<T> load(TvNfoState nfo, Supplier<T> reader) {
        try {
            return new LoadedNfo<>(nfo, Optional.of(reader.get()));
        } catch (NfoException e) {
            log.warn("Ignoring unreadable NFO: {}", e.getMessage());
            return new LoadedNfo<>(nfo, Optional.empty());
        }
    }

    private static TvNfoState written(Path file) {
        return state(file).orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + file));
    }

    private static Optional<TvNfoState> state(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TvNfoState(file, Files.getLastModifiedTime(file).toInstant()));
        } catch (IOException e) {
            log.warn("NFO {} cannot be accessed: {}", file, e.getMessage());
            return Optional.empty();
        }
    }
}
