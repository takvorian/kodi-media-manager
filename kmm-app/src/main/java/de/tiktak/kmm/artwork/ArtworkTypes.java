package de.tiktak.kmm.artwork;

import java.util.List;
import java.util.Locale;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Image types that KMM manages for movies, TV shows, seasons and episodes, and their Kodi names.
 */
final class ArtworkTypes {

    static final List<ImageType> MOVIE_TYPES = List.of(
            ImageType.POSTER,
            ImageType.FANART,
            ImageType.CLEARLOGO,
            ImageType.CLEARART,
            ImageType.DISCART,
            ImageType.BANNER,
            ImageType.LANDSCAPE);

    static final List<ImageType> TV_SHOW_TYPES = List.of(
            ImageType.POSTER,
            ImageType.FANART,
            ImageType.BANNER,
            ImageType.CLEARLOGO,
            ImageType.CLEARART,
            ImageType.LANDSCAPE,
            ImageType.CHARACTERART);

    static final List<ImageType> SEASON_TYPES = List.of(
            ImageType.POSTER,
            ImageType.BANNER,
            ImageType.FANART,
            ImageType.LANDSCAPE);

    static final List<ImageType> EPISODE_TYPES = List.of(ImageType.THUMB);

    /** Like Ember; Kodi reads the same names from the set folder. */
    static final List<ImageType> MOVIE_SET_TYPES = List.of(
            ImageType.POSTER,
            ImageType.FANART,
            ImageType.BANNER,
            ImageType.CLEARLOGO,
            ImageType.CLEARART,
            ImageType.DISCART,
            ImageType.KEYART,
            ImageType.LANDSCAPE);

    private ArtworkTypes() {
    }

    /** Kodi name for file names, e.g. {@code poster} or {@code clearlogo}. */
    static String kodiName(ImageType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
