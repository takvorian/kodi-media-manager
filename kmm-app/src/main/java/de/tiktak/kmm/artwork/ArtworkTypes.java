package de.tiktak.kmm.artwork;

import java.util.List;
import java.util.Locale;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Bildarten, die KMM für Filme, Serien, Staffeln und Episoden verwaltet, und ihre Kodi-Bezeichnungen.
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

    private ArtworkTypes() {
    }

    /** Kodi-Bezeichnung für Dateinamen, z. B. {@code poster} oder {@code clearlogo}. */
    static String kodiName(ImageType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
