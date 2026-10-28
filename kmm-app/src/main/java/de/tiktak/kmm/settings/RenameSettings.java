package de.tiktak.kmm.settings;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;

/**
 * Muster und Auslöser für das Umbenennen, wie Embers Addon „Bulk Rename“. Die automatischen Auslöser sind
 * wie in Ember anfangs aus.
 *
 * @param movieFolder    Ordner eines Films, z. B. {@code {title}[ ({year})]}
 * @param movieFile      Dateien eines Films, z. B. {@code {title}[.{source}]}
 * @param tvShowFolder   Ordner einer Serie
 * @param seasonFolder   Ordner einer Staffel
 * @param episodeFile    Dateien einer Episode
 * @param movieOnEdit    Filme nach dem Speichern der Metadaten umbenennen
 * @param movieOnScrape  Filme nach dem Scrapen umbenennen
 * @param tvOnEdit       Serien nach dem Speichern der Metadaten einer Serie oder Episode umbenennen
 * @param tvOnScrape     Serien und Episoden nach dem Scrapen umbenennen
 * @param episodesOnScan neue Episoden beim Scan umbenennen
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

    public RenameSettings {
        movieFolder = strip(movieFolder);
        movieFile = strip(movieFile);
        tvShowFolder = strip(tvShowFolder);
        seasonFolder = strip(seasonFolder);
        episodeFile = strip(episodeFile);
    }

    /**
     * @throws InvalidSettingsException wenn ein Muster ungültig ist oder ein Dateimuster Ordner enthält
     */
    void validate() {
        check(movieFolder, NamePlaceholders.MOVIE, "Filmordner", true);
        check(movieFile, NamePlaceholders.MOVIE, "Filmdateien", false);
        check(tvShowFolder, NamePlaceholders.TV_SHOW, "Serienordner", false);
        check(seasonFolder, NamePlaceholders.SEASON_FOLDER, "Staffelordner", false);
        check(episodeFile, NamePlaceholders.EPISODE_FILE, "Episodendateien", false);
    }

    private static void check(String pattern, Set<String> placeholders, String label, boolean foldersAllowed) {
        try {
            if (NamePattern.parse(pattern, placeholders).hasFolders() && !foldersAllowed) {
                throw new InvalidSettingsException("Muster für %s: Ordner (/) sind hier nicht erlaubt.".formatted(label));
            }
        } catch (NamePatternException e) {
            throw new InvalidSettingsException("Muster für %s: %s".formatted(label, e.getMessage()));
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
