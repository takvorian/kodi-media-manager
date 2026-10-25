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
 * Verwaltet die Quellen. Öffentliche Schnittstelle des Moduls für andere Module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaSourceService {

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
        log.info("Media source '{}' created for {}", source.getName(), source.getPath());
        return source;
    }

    /**
     * Übernimmt eine Quelle aus einer Migration. Gibt es bereits eine Quelle mit demselben Verzeichnis,
     * wird diese verwendet. Anders als beim Anlegen muss das Verzeichnis nicht erreichbar sein (z. B.
     * ein gerade nicht verbundenes Netzlaufwerk); ist der Name vergeben, erhält er einen Zusatz.
     *
     * @param path absoluter Pfad des Verzeichnisses
     * @throws MediaSourceConflictException wenn sich das Verzeichnis mit einer anderen Quelle überschneidet
     */
    @Transactional
    public ImportedSource importSource(String name, String path, MediaType mediaType) {
        Path directory = Path.of(path.strip()).toAbsolutePath().normalize();
        var existing = repository.findAll().stream()
                .filter(source -> Path.of(source.getPath()).equals(directory))
                .findFirst();
        if (existing.isPresent()) {
            return new ImportedSource(existing.get(), false);
        }
        requireNoOverlap(directory);
        MediaSource source = repository.save(new MediaSource(freeName(name.strip()), directory.toString(), mediaType));
        log.info("Media source '{}' imported for {}", source.getName(), source.getPath());
        return new ImportedSource(source, true);
    }

    @Transactional
    public void delete(long sourceId) {
        MediaSource source = require(sourceId);
        // Die Filme der Quelle löscht die Datenbank per ON DELETE CASCADE.
        repository.delete(source);
        log.info("Media source '{}' deleted", source.getName());
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
