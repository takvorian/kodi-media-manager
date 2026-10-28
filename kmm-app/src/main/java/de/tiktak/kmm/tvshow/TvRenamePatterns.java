package de.tiktak.kmm.tvshow;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;

/**
 * Muster für Serienordner, Staffelordner und Episodendateien, z. B. {@code {show}}, {@code Staffel {season}} und
 * {@code {show} - S{season:2}E{episode:2}[ - {title}]}.
 */
public record TvRenamePatterns(
        @NotBlank @Size(max = MAX_LENGTH) String showFolder,
        @NotBlank @Size(max = MAX_LENGTH) String seasonFolder,
        @NotBlank @Size(max = MAX_LENGTH) String episodeFile) {

    static final int MAX_LENGTH = 512;

    /**
     * @throws TvRenameProblemException wenn ein Muster ungültig ist oder Staffel- bzw. Dateimuster Ordner enthalten
     */
    TvShowRenamePlanner.Patterns parse() {
        return new TvShowRenamePlanner.Patterns(
                parse(showFolder, NamePlaceholders.TV_SHOW, "Serienordner", true),
                parse(seasonFolder, NamePlaceholders.SEASON_FOLDER, "Staffelordner", false),
                parse(episodeFile, NamePlaceholders.EPISODE_FILE, "Episodendateien", false));
    }

    private static NamePattern parse(String text, Set<String> placeholders, String label, boolean foldersAllowed) {
        try {
            NamePattern pattern = NamePattern.parse(text, placeholders);
            if (pattern.hasFolders() && !foldersAllowed) {
                throw TvRenameProblemException.foldersNotAllowed(label);
            }
            return pattern;
        } catch (NamePatternException e) {
            throw TvRenameProblemException.invalidPattern(label, e);
        }
    }
}
