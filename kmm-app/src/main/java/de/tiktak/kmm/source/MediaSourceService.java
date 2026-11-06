package de.tiktak.kmm.source;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.MediaType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the sources. Public interface of the module for other modules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaSourceService {

    /** Prefix of the job key for scans; at most one scan runs per source, regardless of type. */
    public static final String SCAN_JOB_KEY_PREFIX = "scan:";

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

    private final MediaSourceRepository repository;

    public List<MediaSource> findAll() {
        return repository.findAll(BY_NAME);
    }

    public MediaSource require(long sourceId) {
        return repository.findById(sourceId).orElseThrow(() -> new MediaSourceNotFoundException(sourceId));
    }

    @Transactional
    public MediaSource create(CreateMediaSourceCommand command) {
        Path directory = existingDirectory(command.path());
        String name = command.name().strip();
        requireUniqueName(name);
        requireNoOverlap(directory);
        MediaSource source = repository.save(new MediaSource(name, directory.toString(), command.mediaType()));
        source.changeMetadataLanguage(command.metadataLanguage());
        log.info("Media source '{}' created for {}", source.getName(), source.getPath());
        return source;
    }

    /**
     * Takes over a source from a migration. If a source with the same directory already exists,
     * that one is used. Unlike when creating, the directory need not be reachable (e.g.
     * a network drive that is currently not connected); if the name is taken, a suffix is added.
     *
     * @param path     absolute path of the directory
     * @param language language of the source or {@code null}; an existing source keeps its own language
     * @throws MediaSourceConflictException if the directory overlaps with another source
     */
    @Transactional
    public ImportedSource importSource(String name, String path, MediaType mediaType, String language) {
        Path directory = Path.of(path.strip()).toAbsolutePath().normalize();
        var existing = repository.findAll().stream()
                .filter(source -> Path.of(source.getPath()).equals(directory))
                .findFirst();
        if (existing.isPresent()) {
            if (existing.get().getMetadataLanguage() == null) {
                existing.get().changeMetadataLanguage(language);
            }
            return new ImportedSource(existing.get(), false);
        }
        requireNoOverlap(directory);
        MediaSource source = repository.save(new MediaSource(freeName(name.strip()), directory.toString(), mediaType));
        source.changeMetadataLanguage(language);
        log.info("Media source '{}' imported for {}", source.getName(), source.getPath());
        return new ImportedSource(source, true);
    }

    @Transactional
    public void delete(long sourceId) {
        MediaSource source = require(sourceId);
        // The database deletes the movies of the source via ON DELETE CASCADE.
        repository.delete(source);
        log.info("Media source '{}' deleted", source.getName());
    }

    /**
     * @param language language such as {@code de-DE} or {@code null}, in which case the general settings apply
     */
    @Transactional
    public MediaSource changeLanguage(long sourceId, String language) {
        MediaSource source = require(sourceId);
        source.changeMetadataLanguage(language);
        log.info("Language of media source '{}' set to {}", source.getName(), language);
        return source;
    }

    /** The language of the source or {@code null} if the general settings apply. */
    @Transactional(readOnly = true)
    public String languageOf(long sourceId) {
        return repository.findById(sourceId).map(MediaSource::getMetadataLanguage).orElse(null);
    }

    @Transactional
    public void markScanned(long sourceId, Instant scannedAt) {
        require(sourceId).markScanned(scannedAt);
    }

    private static Path existingDirectory(String path) {
        try {
            Path directory = Path.of(path.strip()).toAbsolutePath().normalize();
            if (!Files.isDirectory(directory)) {
                throw new InvalidMediaSourcePathException(path);
            }
            return directory;
        } catch (InvalidPathException e) {
            throw new InvalidMediaSourcePathException(path);
        }
    }

    private void requireUniqueName(String name) {
        if (repository.existsByNameIgnoreCase(name)) {
            throw MediaSourceConflictException.nameTaken(name);
        }
    }

    private String freeName(String name) {
        String candidate = name;
        for (int suffix = 2; repository.existsByNameIgnoreCase(candidate); suffix++) {
            candidate = "%s (%d)".formatted(name, suffix);
        }
        return candidate;
    }

    private void requireNoOverlap(Path directory) {
        repository.findAll().stream()
                .filter(existing -> overlaps(directory, Path.of(existing.getPath())))
                .findFirst()
                .ifPresent(existing -> {
                    throw MediaSourceConflictException.overlaps(directory.toString(), existing);
                });
    }

    private static boolean overlaps(Path first, Path second) {
        return first.startsWith(second) || second.startsWith(first);
    }
}
