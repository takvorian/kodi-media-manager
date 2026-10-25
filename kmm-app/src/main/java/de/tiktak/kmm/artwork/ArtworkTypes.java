package de.tiktak.kmm.artwork;

import java.util.List;
import java.util.Locale;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Bildarten, die KMM für Filme verwaltet, und ihre Kodi-Bezeichnungen.
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

    private ArtworkTypes() {
    }

    /** Kodi-Bezeichnung für Dateinamen, z. B. {@code poster} oder {@code clearlogo}. */
    static String kodiName(ImageType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
