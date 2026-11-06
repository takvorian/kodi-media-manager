package de.tiktak.kmm.movie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;

/**
 * Patterns for the folders and files of a movie, e.g. {@code {title}[ ({year})]} and {@code {title}[.{source}]}.
 */
public record MovieRenamePatterns(
        @NotBlank @Size(max = MAX_LENGTH) String folder,
        @NotBlank @Size(max = MAX_LENGTH) String file) {

    static final int MAX_LENGTH = 512;
    private static final LocalizedText FOLDER_PATTERN = LocalizedText.of("movie.rename.pattern.folder");
    private static final LocalizedText FILE_PATTERN = LocalizedText.of("movie.rename.pattern.file");

    /**
     * @throws MovieRenameProblemException if a pattern is invalid or the file pattern contains folders
     */
    Parsed parse() {
        NamePattern folderPattern = parse(folder, FOLDER_PATTERN);
        NamePattern filePattern = parse(file, FILE_PATTERN);
        if (filePattern.hasFolders()) {
            throw MovieRenameProblemException.folderInFilePattern();
        }
        return new Parsed(folderPattern, filePattern);
    }

    private static NamePattern parse(String text, LocalizedText label) {
        try {
            return NamePattern.parse(text, NamePlaceholders.MOVIE);
        } catch (NamePatternException e) {
            throw MovieRenameProblemException.invalidPattern(label, e);
        }
    }

    record Parsed(NamePattern folder, NamePattern file) {
    }
}
