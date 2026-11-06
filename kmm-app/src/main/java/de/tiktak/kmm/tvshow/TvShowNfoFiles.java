package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.EpisodeNfoReader;
import de.tiktak.kmm.nfo.EpisodeNfoWriter;
import de.tiktak.kmm.nfo.EpisodeStreamDetails;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.NfoPlayback;
import de.tiktak.kmm.nfo.PlaybackNfoWriter;
import de.tiktak.kmm.nfo.StreamDetailsNfoWriter;
import de.tiktak.kmm.nfo.LanguageNfoWriter;
import de.tiktak.kmm.nfo.TrailerNfoWriter;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.nfo.TvShowNfoReader;
import de.tiktak.kmm.nfo.TvShowNfoWriter;
import de.tiktak.kmm.scanner.TvShowFiles;
import lombok.extern.slf4j.Slf4j;

/**
 * Access to the NFOs of TV shows ({@code tvshow.nfo}) and episodes: find, read and write.
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
    private final TrailerNfoWriter trailerWriter = new TrailerNfoWriter();
    private final LanguageNfoWriter languageWriter = new LanguageNfoWriter();

    Optional<TvNfoState> findShowNfo(Path showDirectory) {
        return state(TvShowFiles.tvShowNfo(showDirectory));
    }

    Optional<TvNfoState> findEpisodeNfo(Path episodeFile) {
        return state(TvShowFiles.episodeNfo(episodeFile));
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException if the file cannot be read
     */
    TvShowNfo readShow(TvNfoState nfo) {
        return showReader.read(nfo.file());
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException if the file cannot be read
     */
    List<EpisodeNfo> readEpisodes(TvNfoState nfo) {
        return episodeReader.read(nfo.file());
    }

    /** Reads the {@code tvshow.nfo}; an unreadable file is logged. */
    LoadedNfo<TvShowNfo> loadShow(TvNfoState nfo) {
        return load(nfo, () -> readShow(nfo));
    }

    /** Reads the NFO of an episode file; an unreadable file is logged. */
    LoadedNfo<List<EpisodeNfo>> loadEpisodes(TvNfoState nfo) {
        return load(nfo, () -> readEpisodes(nfo));
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException if the file cannot be written
     */
    TvNfoState writeShow(Path showDirectory, TvShowNfo nfo) {
        Path file = TvShowFiles.tvShowNfo(showDirectory);
        showWriter.write(file, nfo);
        return written(file);
    }

    /**
     * @throws de.tiktak.kmm.nfo.NfoException if the file cannot be written
     */
    TvNfoState writeEpisodes(Path episodeFile, List<EpisodeNfo> episodes) {
        Path file = TvShowFiles.episodeNfo(episodeFile);
        episodeWriter.write(file, episodes);
        return written(file);
    }

    /**
     * Writes only the playback state and own rating of the episode into its existing NFO.
     *
     * @throws de.tiktak.kmm.nfo.NfoException if the NFO does not contain the episode or cannot be
     *                                        written
     */
    TvNfoState writePlayback(TvNfoState nfo, int season, int episode, NfoPlayback playback) {
        playbackWriter.writeEpisode(nfo.file(), season, episode, playback);
        return written(nfo.file());
    }

    /**
     * Replaces only the technical details of an episode in the existing NFO.
     *
     * @throws NfoException if the NFO does not contain the episode or cannot be written
     */
    TvNfoState writeStreamDetails(TvNfoState nfo, List<EpisodeStreamDetails> episodes) {
        streamDetailsWriter.writeEpisodes(nfo.file(), episodes);
        return written(nfo.file());
    }

    /**
     * Changes only the language in the existing {@code tvshow.nfo}; everything else stays character for character.
     *
     * @param language language, or {@code null} to remove it
     * @throws NfoException if the NFO does not contain a TV show or cannot be written
     */
    TvNfoState writeShowLanguage(TvNfoState nfo, String language) {
        languageWriter.writeTvShow(nfo.file(), language);
        return written(nfo.file());
    }

    /**
     * Changes only the trailer link in the existing {@code tvshow.nfo}; everything else stays character for character.
     *
     * @param trailer link, or {@code null} to remove it
     * @throws NfoException if the NFO does not contain a TV show or cannot be written
     */
    TvNfoState writeShowTrailer(TvNfoState nfo, String trailer) {
        trailerWriter.writeTvShow(nfo.file(), trailer);
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
