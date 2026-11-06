package de.tiktak.kmm.settings;

import jakarta.validation.constraints.Size;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Settings for movie sets. Set artwork lives outside the movie folders, optionally in Ember's flat scheme
 * ({@code <folder>/<set title>-poster.jpg}) and in Kodi's "movie set information folder"
 * ({@code <folder>/<set title>/poster.jpg}); each of them can be switched off.
 *
 * @param flatImages       store set artwork in the flat scheme
 * @param flatImagesFolder folder for the flat scheme
 * @param kodiImages       store set artwork in Kodi's set folders
 * @param kodiImagesFolder folder in which Kodi expects a subfolder per set
 * @param assignOnScrape   assign movies to the set of their TMDB collection when scraping, like Ember's
 *                         "CollectionsAuto"; another set is replaced
 */
public record MovieSetSettings(
        boolean flatImages,
        @Size(max = KmmSettings.MAX_PATH_LENGTH) String flatImagesFolder,
        boolean kodiImages,
        @Size(max = KmmSettings.MAX_PATH_LENGTH) String kodiImagesFolder,
        boolean assignOnScrape) {

    public static final MovieSetSettings DEFAULTS = new MovieSetSettings(false, null, false, null, false);

    public MovieSetSettings {
        flatImagesFolder = blankToNull(flatImagesFolder);
        kodiImagesFolder = blankToNull(kodiImagesFolder);
    }

    /**
     * @throws InvalidSettingsException if a storage is switched on without a folder
     */
    void validate() {
        if (flatImages && flatImagesFolder == null) {
            throw new InvalidSettingsException(LocalizedText.of("settings.movieSets.flatFolderMissing"));
        }
        if (kodiImages && kodiImagesFolder == null) {
            throw new InvalidSettingsException(LocalizedText.of("settings.movieSets.kodiFolderMissing"));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
