package de.tiktak.kmm.theme;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads, saves and deletes {@code theme.mp3}. Downloading goes to a temporary file next to the theme music, so
 * that errors do not leave a half-written file.
 */
@Slf4j
@Component
class ThemeFiles {

    private static final String TEMP_SUFFIX = ".kmm-tmp";

    ThemeInfo info(ThemeTarget target) {
        Path file = target.themeFile();
        if (!Files.isRegularFile(file)) {
            return new ThemeInfo(false, file.toString(), null, null);
        }
        try {
            return new ThemeInfo(true, file.toString(), Files.size(file), Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            log.warn("Theme {} cannot be accessed: {}", file, e.getMessage());
            return new ThemeInfo(false, file.toString(), null, null);
        }
    }

    Optional<Path> find(ThemeTarget target) {
        Path file = target.themeFile();
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /**
     * @throws de.tiktak.kmm.plugin.api.ScraperException if the provider does not deliver the music
     * @throws ThemeProblemException                     if the file cannot be written
     */
    void save(ThemeTarget target, ThemeScraper scraper, ThemeCandidate candidate) {
        Path file = target.themeFile();
        Path temporary = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try {
            scraper.download(candidate, temporary);
            replace(temporary, file);
        } catch (IOException e) {
            throw ThemeProblemException.unwritable(file, e);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /** @throws ThemeProblemException if the file cannot be deleted */
    void delete(ThemeTarget target) {
        Path file = target.themeFile();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw ThemeProblemException.unwritable(file, e);
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

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted: {}", file, e.getMessage());
        }
    }
}
