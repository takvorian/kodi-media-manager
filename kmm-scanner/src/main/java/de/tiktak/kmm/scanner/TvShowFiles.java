package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Begleitdateien von Serien und Episoden nach Kodi-Konvention (Kodi 19–21):
 * <ul>
 * <li>Serie: {@code tvshow.nfo}, {@code poster.jpg}, {@code fanart.jpg} … im Serienordner</li>
 * <li>Staffel: {@code season01-poster.jpg}, {@code season-specials-poster.jpg} für Staffel 0 und
 * {@code season-all-poster.jpg} für alle Staffeln, ebenfalls im Serienordner</li>
 * <li>Episode: {@code <dateiname>.nfo} und {@code <dateiname>-thumb.jpg} neben der Videodatei</li>
 * </ul>
 */
public final class TvShowFiles {

    static final String TV_SHOW_NFO = "tvshow.nfo";
    static final String EPISODE_THUMB = "thumb";
    private static final String NFO_EXTENSION = ".nfo";
    private static final String SEASON_SPECIALS = "season-specials";
    private static final String SEASON_ALL = "season-all";
    private static final char EXTENSION_SEPARATOR = '.';
    private static final char ARTWORK_SEPARATOR = '-';

    private TvShowFiles() {
    }

    public static Path tvShowNfo(Path showDirectory) {
        return showDirectory.resolve(TV_SHOW_NFO);
    }

    /** Mögliche Bilddateien einer Serie, z. B. {@code poster.jpg} oder {@code poster.png}. */
    public static List<Path> tvShowArtworkCandidates(Path showDirectory, String kodiName) {
        return CompanionFiles.ARTWORK_EXTENSIONS.stream()
                .map(extension -> showDirectory.resolve(kodiName + EXTENSION_SEPARATOR + extension))
                .toList();
    }

    /**
     * @param season Staffel oder {@code null} für das Bild aller Staffeln
     */
    public static List<Path> seasonArtworkCandidates(Path showDirectory, Integer season, String kodiName) {
        String prefix = seasonPrefix(season);
        return CompanionFiles.ARTWORK_EXTENSIONS.stream()
                .map(extension -> showDirectory.resolve(prefix + ARTWORK_SEPARATOR + kodiName + EXTENSION_SEPARATOR
                        + extension))
                .toList();
    }

    public static Path episodeNfo(Path episodeFile) {
        return Objects.requireNonNull(episodeFile.getParent(), "episode file needs a directory")
                .resolve(baseName(episodeFile) + NFO_EXTENSION);
    }

    public static List<Path> episodeThumbCandidates(Path episodeFile) {
        String base = baseName(episodeFile) + ARTWORK_SEPARATOR + EPISODE_THUMB + EXTENSION_SEPARATOR;
        return CompanionFiles.ARTWORK_EXTENSIONS.stream()
                .map(extension -> episodeFile.resolveSibling(base + extension))
                .toList();
    }

    private static String seasonPrefix(Integer season) {
        if (season == null) {
            return SEASON_ALL;
        }
        return season == 0 ? SEASON_SPECIALS : String.format(Locale.ROOT, "season%02d", season);
    }

    private static String baseName(Path file) {
        String fileName = file.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        return extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
    }
}
