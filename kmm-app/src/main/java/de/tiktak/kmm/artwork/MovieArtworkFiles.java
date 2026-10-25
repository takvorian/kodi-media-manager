package de.tiktak.kmm.artwork;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.scanner.CompanionFiles;
import lombok.extern.slf4j.Slf4j;

/**
 * Bilddateien eines Films im Dateisystem: finden, ersetzen und löschen. Ein neues Bild ersetzt
 * auch Varianten mit anderer Endung (z. B. {@code -poster.png} durch {@code -poster.jpg}).
 */
@Slf4j
@Component
class MovieArtworkFiles {

    private static final String TEMP_SUFFIX = ".kmm-tmp";

    Optional<ArtworkFile> find(CompanionFiles files, ImageType type) {
        return files.artworkCandidates(ArtworkTypes.kodiName(type)).stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .flatMap(file -> state(type, file));
    }

    ArtworkFile save(CompanionFiles files, ImageType type, DownloadedImage image) {
        Path target = files.artworkFile(ArtworkTypes.kodiName(type), image.extension());
        Path temporary = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Files.write(temporary, image.content());
            replace(temporary, target);
            deleteOtherVariants(files, type, target);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw ArtworkProblemException.unwritable(target, e);
        }
        return state(type, target).orElseThrow(() -> new IllegalStateException("Saved image is missing: " + target));
    }

    void delete(CompanionFiles files, ImageType type) {
        for (Path file : files.artworkCandidates(ArtworkTypes.kodiName(type))) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw ArtworkProblemException.unwritable(file, e);
            }
        }
    }

    private void deleteOtherVariants(CompanionFiles files, ImageType type, Path keep) throws IOException {
        for (Path variant : files.artworkCandidates(ArtworkTypes.kodiName(type))) {
            if (!variant.equals(keep)) {
                Files.deleteIfExists(variant);
            }
        }
    }

    /** Netzlaufwerke unterstützen teils kein atomares Ersetzen; dann wird normal verschoben. */
    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Optional<ArtworkFile> state(ImageType type, Path file) {
        try {
            return Optional.of(new ArtworkFile(type, file, Files.getLastModifiedTime(file).toInstant()));
        } catch (IOException e) {
            log.warn("Image {} cannot be accessed: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted: {}", file, e.getMessage());
        }
    }
}
