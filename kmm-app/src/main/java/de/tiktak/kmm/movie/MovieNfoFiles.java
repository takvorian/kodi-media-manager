package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.LanguageNfoWriter;
import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.NfoPlayback;
import de.tiktak.kmm.nfo.NfoReader;
import de.tiktak.kmm.nfo.NfoWriter;
import de.tiktak.kmm.nfo.PlaybackNfoWriter;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.StreamDetailsNfoWriter;
import de.tiktak.kmm.nfo.TrailerNfoWriter;
import de.tiktak.kmm.scanner.CompanionFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Access to the NFO file of a movie: find, read and write. An existing NFO is updated at
 * its previous location (also a {@code movie.nfo}) so that no second file is created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieNfoFiles {

    private final NfoReader reader;
    private final NfoWriter writer;
    private final PlaybackNfoWriter playbackWriter = new PlaybackNfoWriter();
    private final StreamDetailsNfoWriter streamDetailsWriter = new StreamDetailsNfoWriter();
    private final TrailerNfoWriter trailerWriter = new TrailerNfoWriter();
    private final LanguageNfoWriter languageWriter = new LanguageNfoWriter();

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
     * Writes the metadata to the movie's NFO.
     *
     * @param supplement further details, or {@code null} to leave the existing ones in the file
     * @return state of the written file
     * @throws NfoException if the file cannot be written
     */
    NfoFileState write(CompanionFiles companionFiles, MovieMetadata metadata, MovieNfoSupplement supplement) {
        Optional<NfoFileState> existing = find(companionFiles);
        Path target = existing.map(NfoFileState::file).orElseGet(companionFiles::nfoFile);
        MovieNfo nfo = MovieNfoMapper.toNfo(metadata, supplement, existingUniqueIds(existing));
        writer.write(target, nfo);
        return state(target).orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + target));
    }

    /**
     * Writes only the playback state and own rating to the existing NFO.
     *
     * @return state of the written file
     * @throws NfoException if the file does not contain a movie or cannot be written
     */
    NfoFileState writePlayback(NfoFileState nfoFile, NfoPlayback playback) {
        playbackWriter.writeMovie(nfoFile.file(), playback);
        return state(nfoFile.file())
                .orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + nfoFile.file()));
    }

    /**
     * Replaces only the technical details in the existing NFO.
     *
     * @return state of the written file
     * @throws NfoException if the file does not contain a movie or cannot be written
     */
    NfoFileState writeStreamDetails(NfoFileState nfoFile, StreamDetails details) {
        streamDetailsWriter.writeMovie(nfoFile.file(), details);
        return state(nfoFile.file())
                .orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + nfoFile.file()));
    }

    /**
     * Changes only the trailer link in the existing NFO; everything else stays character for character.
     *
     * @param trailer link, or {@code null} to remove it
     * @return state of the written file
     * @throws NfoException if the file does not contain a movie or cannot be written
     */
    NfoFileState writeTrailer(NfoFileState nfoFile, String trailer) {
        trailerWriter.writeMovie(nfoFile.file(), trailer);
        return state(nfoFile.file())
                .orElseThrow(() -> new IllegalStateException("Written NFO is missing: " + nfoFile.file()));
    }

    /**
     * Changes only the language in the existing NFO; everything else stays character for character.
     *
     * @param language language, or {@code null} to remove it
     * @return state of the written file
     * @throws NfoException if the file does not contain a movie or cannot be written
     */
    NfoFileState writeLanguage(NfoFileState nfoFile, String language) {
        languageWriter.writeMovie(nfoFile.file(), language);
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
