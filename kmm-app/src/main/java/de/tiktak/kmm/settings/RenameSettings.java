package de.tiktak.kmm.settings;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;
import de.tiktak.kmm.web.LocalizedTexts;

/**
 * Patterns and triggers for renaming, like Ember's add-on "Bulk Rename". As in Ember, the automatic triggers are
 * off at first.
 *
 * @param movieFolder    folder of a movie, e.g. {@code {title}[ ({year})]}
 * @param movieFile      files of a movie, e.g. {@code {title}[.{source}]}
 * @param tvShowFolder   folder of a TV show
 * @param seasonFolder   folder of a season
 * @param episodeFile    files of an episode
 * @param movieOnEdit    rename movies after their metadata was saved
 * @param movieOnScrape  rename movies after scraping
 * @param tvOnEdit       rename TV shows after the metadata of a show or episode was saved
 * @param tvOnScrape     rename TV shows and episodes after scraping
 * @param episodesOnScan rename new episodes during the scan
 */
public record RenameSettings(
        @NotBlank @Size(max = MAX_PATTERN_LENGTH) String movieFolder,
        @NotBlank @Size(max = MAX_PATTERN_LENGTH) String movieFile,
        @NotBlank @Size(max = MAX_PATTERN_LENGTH) String tvShowFolder,
        @NotBlank @Size(max = MAX_PATTERN_LENGTH) String seasonFolder,
        @NotBlank @Size(max = MAX_PATTERN_LENGTH) String episodeFile,
        boolean movieOnEdit,
        boolean movieOnScrape,
        boolean tvOnEdit,
        boolean tvOnScrape,
        boolean episodesOnScan) {

    public static final int MAX_PATTERN_LENGTH = 512;

    public static final RenameSettings DEFAULTS = new RenameSettings("{title}[ ({year})]", "{title}[.{source}]",
            "{show}", "Season {season:2}", "{show} - S{season:2}E{episode:2}[ - {title}]", false, false, false, false,
            false);

    private static final String PATTERN_KEY = "settings.rename.pattern.";

    public RenameSettings {
        movieFolder = strip(movieFolder);
        movieFile = strip(movieFile);
        tvShowFolder = strip(tvShowFolder);
        seasonFolder = strip(seasonFolder);
        episodeFile = strip(episodeFile);
    }

    /**
     * @throws InvalidSettingsException if a pattern is invalid or a file pattern contains folders
     */
    void validate() {
        check(movieFolder, NamePlaceholders.MOVIE, "movieFolder", true);
        check(movieFile, NamePlaceholders.MOVIE, "movieFile", false);
        check(tvShowFolder, NamePlaceholders.TV_SHOW, "tvShowFolder", false);
        check(seasonFolder, NamePlaceholders.SEASON_FOLDER, "seasonFolder", false);
        check(episodeFile, NamePlaceholders.EPISODE_FILE, "episodeFile", false);
    }

    private static void check(String pattern, Set<String> placeholders, String name, boolean foldersAllowed) {
        LocalizedText label = LocalizedText.of(PATTERN_KEY + name);
        try {
            if (NamePattern.parse(pattern, placeholders).hasFolders() && !foldersAllowed) {
                throw new InvalidSettingsException(
                        LocalizedText.of("settings.rename.foldersNotAllowed").with("pattern", label));
            }
        } catch (NamePatternException e) {
            throw new InvalidSettingsException(LocalizedText.of("settings.rename.invalidPattern")
                    .with("pattern", label)
                    .with("reason", LocalizedTexts.of(e)));
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
