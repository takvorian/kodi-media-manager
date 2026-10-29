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
 * Liest, speichert und löscht {@code theme.mp3}. Geladen wird in eine temporäre Datei neben der Titelmusik, damit bei
 * Fehlern keine halbe Datei entsteht.
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
     * @throws de.tiktak.kmm.plugin.api.ScraperException wenn der Anbieter die Musik nicht liefert
     * @throws ThemeProblemException                     wenn die Datei nicht geschrieben werden kann
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

    /** @throws ThemeProblemException wenn die Datei nicht gelöscht werden kann */
    void delete(ThemeTarget target) {
        Path file = target.themeFile();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw ThemeProblemException.unwritable(file, e);
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

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted: {}", file, e.getMessage());
        }
    }
}
