package de.tiktak.kmm.migration.ember.settings;

/**
 * Embers Einstellungen für Filmsets. Ein Wert ist {@code null}, wenn die Datei ihn nicht enthält.
 *
 * @param flatImages       ob Set-Bilder im flachen Schema liegen ({@code MovieSetUseExtended})
 * @param flatImagesFolder Ordner des flachen Schemas ({@code MovieSetPathExtended})
 * @param kodiImages       ob Set-Bilder in Kodis Set-Ordnern liegen ({@code MovieSetUseMatrix})
 * @param kodiImagesFolder Ordner der Kodi-Set-Ordner ({@code MovieSetPathMatrix})
 * @param assignOnScrape   ob Filme beim Scrapen ihrem Set zugeordnet werden ({@code MovieScraperCollectionsAuto})
 */
public record EmberMovieSetSettings(Boolean flatImages, String flatImagesFolder, Boolean kodiImages,
        String kodiImagesFolder, Boolean assignOnScrape) {

    public static final EmberMovieSetSettings NONE = new EmberMovieSetSettings(null, null, null, null, null);
}
