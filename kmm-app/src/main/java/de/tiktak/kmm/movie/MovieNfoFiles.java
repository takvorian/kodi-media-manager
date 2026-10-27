package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.NfoReader;
import de.tiktak.kmm.nfo.NfoWriter;
import de.tiktak.kmm.nfo.PlaybackNfoWriter;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.StreamDetailsNfoWriter;
import de.tiktak.kmm.scanner.CompanionFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Zugriff auf die NFO-Datei eines Films: finden, lesen und schreiben. Eine vorhandene NFO wird an
 * ihrem bisherigen Ort aktualisiert (auch eine {@code movie.nfo}), damit keine zweite Datei entsteht.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieNfoFiles {

    private final NfoReader reader;
    private final NfoWriter writer;
    private final PlaybackNfoWriter playbackWriter = new PlaybackNfoWriter();
    private final StreamDetailsNfoWriter streamDetailsWriter = new StreamDetailsNfoWriter();

    Optional<NfoFileState> find(CompanionFiles companionFiles) {
        return companionFiles.nfoCandidates().stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .flatMap(MovieNfoFiles::state);
    }

    MovieNfo read(NfoFileState nfoFile) {
        return reader.read(nfoFile.file());
    }

    /**
     * Schreibt die Metadaten in die NFO des Films.
     *
     * @param supplement weitere Angaben oder {@code null}, um die vorhandenen in der Datei zu belassen
     * @return Zustand der geschriebenen Datei
     * @throws NfoException wenn die Datei nicht geschrieben werden kann
     */
    NfoFileState write(CompanionFiles companionFiles, MovieMetadata metadata, MovieNfoSupplement supplement) {
        Optional<NfoFileState> existing = find(companionFiles);
        Path target = existing.map(NfoFileState::file).orElseGet(companionFiles::nfoFile);
        MovieNfo nfo = MovieNfoMapper.toNfo(metadata, supplement, existingUniqueIds(existing));
        writer.write(target, nfo);
        return state(target).orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + target));
    }

    /**
     * Schreibt nur den Wiedergabestand in die vorhandene NFO.
     *
     * @return Zustand der geschriebenen Datei
     * @throws NfoException wenn die Datei keinen Film enthält oder nicht geschrieben werden kann
     */
    NfoFileState writePlayback(NfoFileState nfoFile, Integer playCount, LocalDateTime lastPlayed) {
        playbackWriter.writeMovie(nfoFile.file(), playCount, lastPlayed);
        return state(nfoFile.file())
                .orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + nfoFile.file()));
    }

    /**
     * Ersetzt nur die technischen Angaben in der vorhandenen NFO.
     *
     * @return Zustand der geschriebenen Datei
     * @throws NfoException wenn die Datei keinen Film enthält oder nicht geschrieben werden kann
     */
    NfoFileState writeStreamDetails(NfoFileState nfoFile, StreamDetails details) {
        streamDetailsWriter.writeMovie(nfoFile.file(), details);
        return state(nfoFile.file())
                .orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + nfoFile.file()));
    }

    private Map<String, String> existingUniqueIds(Optional<NfoFileState> existing) {
        try {
            return existing.map(nfoFile -> read(nfoFile).uniqueIds()).orElse(Map.of());
        } catch (NfoException e) {
            log.warn("Existing NFO is unreadable, its IDs are not preserved: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Optional<NfoFileState> state(Path file) {
        try {
            Instant modifiedAt = Files.getLastModifiedTime(file).toInstant();
            return Optional.of(new NfoFileState(file, modifiedAt));
        } catch (IOException e) {
            log.warn("NFO {} cannot be accessed: {}", file, e.getMessage());
            return Optional.empty();
        }
    }
}
