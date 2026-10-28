package de.tiktak.kmm.settings;

import jakarta.validation.constraints.Size;

/**
 * Einstellungen für Filmsets. Set-Bilder liegen außerhalb der Filmordner, wahlweise in Embers flachem Schema
 * ({@code <Ordner>/<Settitel>-poster.jpg}) und in Kodis „Ordner für Filmset-Informationen“
 * ({@code <Ordner>/<Settitel>/poster.jpg}); beide sind einzeln abschaltbar.
 *
 * @param flatImages       Set-Bilder im flachen Schema ablegen
 * @param flatImagesFolder Ordner für das flache Schema
 * @param kodiImages       Set-Bilder in Kodis Set-Ordnern ablegen
 * @param kodiImagesFolder Ordner, in dem Kodi je Set einen Unterordner erwartet
 * @param assignOnScrape   Filme beim Scrapen dem Set ihrer TMDB-Sammlung zuordnen, wie Embers
 *                         „CollectionsAuto“; ein anderes Set wird dabei ersetzt
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
     * @throws InvalidSettingsException wenn eine Ablage eingeschaltet ist, aber kein Ordner angegeben ist
     */
    void validate() {
        if (flatImages && flatImagesFolder == null) {
            throw new InvalidSettingsException("Für Set-Bilder im flachen Schema fehlt der Ordner.");
        }
        if (kodiImages && kodiImagesFolder == null) {
            throw new InvalidSettingsException("Für Set-Bilder in Kodis Set-Ordnern fehlt der Ordner.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
