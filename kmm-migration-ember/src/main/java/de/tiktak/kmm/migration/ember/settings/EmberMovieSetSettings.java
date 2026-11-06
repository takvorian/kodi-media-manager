package de.tiktak.kmm.migration.ember.settings;

/**
 * Ember's settings for movie sets. A value is {@code null} if the file does not contain it.
 *
 * @param flatImages       whether set images use the flat scheme ({@code MovieSetUseExtended})
 * @param flatImagesFolder folder of the flat scheme ({@code MovieSetPathExtended})
 * @param kodiImages       whether set images lie in Kodi's set folders ({@code MovieSetUseMatrix})
 * @param kodiImagesFolder folder of the Kodi set folders ({@code MovieSetPathMatrix})
 * @param assignOnScrape   whether movies are assigned to their set when scraping ({@code MovieScraperCollectionsAuto})
 */
public record EmberMovieSetSettings(Boolean flatImages, String flatImagesFolder, Boolean kodiImages,
        String kodiImagesFolder, Boolean assignOnScrape) {

    public static final EmberMovieSetSettings NONE = new EmberMovieSetSettings(null, null, null, null, null);
}
