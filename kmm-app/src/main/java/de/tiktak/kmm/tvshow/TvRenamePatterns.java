package de.tiktak.kmm.tvshow;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;

/**
 * Patterns for TV show folders, season folders and episode files, e.g. {@code {show}}, {@code Season {season}} and
 * {@code {show} - S{season:2}E{episode:2}[ - {title}]}.
 */
public record TvRenamePatterns(
        @NotBlank @Size(max = MAX_LENGTH) String showFolder,
        @NotBlank @Size(max = MAX_LENGTH) String seasonFolder,
        @NotBlank @Size(max = MAX_LENGTH) String episodeFile) {

    static final int MAX_LENGTH = 512;
    private static final String LABEL_KEY = "tvShow.rename.pattern.";

    /**
     * @throws TvRenameProblemException if a pattern is invalid or the season or file pattern contains folders
     */
    TvShowRenamePlanner.Patterns parse() {
        return new TvShowRenamePlanner.Patterns(
                parse(showFolder, NamePlaceholders.TV_SHOW, "showFolder", true),
                parse(seasonFolder, NamePlaceholders.SEASON_FOLDER, "seasonFolder", false),
                parse(episodeFile, NamePlaceholders.EPISODE_FILE, "episodeFile", false));
    }

    private static NamePattern parse(String text, Set<String> placeholders, String name, boolean foldersAllowed) {
        LocalizedText label = LocalizedText.of(LABEL_KEY + name);
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
