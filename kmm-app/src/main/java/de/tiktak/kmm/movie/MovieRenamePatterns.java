package de.tiktak.kmm.movie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;

/**
 * Muster für Ordner und Dateien eines Films, z. B. {@code {title}[ ({year})]} und {@code {title}[.{source}]}.
 */
public record MovieRenamePatterns(
        @NotBlank @Size(max = MAX_LENGTH) String folder,
        @NotBlank @Size(max = MAX_LENGTH) String file) {

    static final int MAX_LENGTH = 512;

    /**
     * @throws MovieRenameProblemException wenn ein Muster ungültig ist oder das Dateimuster Ordner enthält
     */
    Parsed parse() {
        NamePattern folderPattern = parse(folder, "Ordnermuster");
        NamePattern filePattern = parse(file, "Dateimuster");
        if (filePattern.hasFolders()) {
            throw MovieRenameProblemException.folderInFilePattern();
        }
        return new Parsed(folderPattern, filePattern);
    }

    private static NamePattern parse(String text, String label) {
        try {
            return NamePattern.parse(text, NamePlaceholders.MOVIE);
        } catch (NamePatternException e) {
            throw MovieRenameProblemException.invalidPattern(label, e);
        }
    }

    record Parsed(NamePattern folder, NamePattern file) {
    }
}
