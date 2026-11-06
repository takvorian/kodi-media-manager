package de.tiktak.kmm.artwork;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ImageType;
import lombok.extern.slf4j.Slf4j;

/**
 * Image files in the file system: find, replace and delete. A new image also replaces variants with a
 * different extension (e.g. {@code poster.png} by {@code poster.jpg}).
 */
@Slf4j
@Component
class ArtworkFiles {

    private static final String TEMP_SUFFIX = ".kmm-tmp";

    Optional<ArtworkFile> find(ArtworkSlot slot) {
        return slot.candidates().stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .flatMap(file -> state(slot.type(), file));
    }

    ArtworkFile save(ArtworkSlot slot, DownloadedImage image) {
        Path target = slot.target(image.extension());
        Path temporary = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Files.write(temporary, image.content());
            replace(temporary, target);
            deleteOtherVariants(slot, target);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw ArtworkProblemException.unwritable(target, e);
        }
        return state(slot.type(), target).orElseThrow(() -> new IllegalStateException("Saved image is missing: " + target));
    }

    void delete(ArtworkSlot slot) {
        for (Path file : slot.candidates()) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw ArtworkProblemException.unwritable(file, e);
            }
        }
    }

    private void deleteOtherVariants(ArtworkSlot slot, Path keep) throws IOException {
        for (Path variant : slot.candidates()) {
            if (!variant.equals(keep)) {
                Files.deleteIfExists(variant);
            }
        }
    }

    /** Network drives sometimes do not support atomic replacement; then a normal move is used. */
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
