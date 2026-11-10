package de.tiktak.kmm.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Companion files of TV shows and episodes according to Kodi's convention (Kodi 19–21):
 * <ul>
 * <li>TV show: {@code tvshow.nfo}, {@code poster.jpg}, {@code fanart.jpg} … in the TV show folder</li>
 * <li>season: {@code season01-poster.jpg}, {@code season-specials-poster.jpg} for season 0 and
 * {@code season-all-poster.jpg} for all seasons, also in the TV show folder</li>
 * <li>episode: {@code <filename>.nfo} and {@code <filename>-thumb.jpg} next to the video file</li>
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

    /** Possible image files of a TV show, e.g. {@code poster.jpg} or {@code poster.png}. */
    public static List<Path> tvShowArtworkCandidates(Path showDirectory, String kodiName) {
        return CompanionFiles.ARTWORK_EXTENSIONS.stream()
                .map(extension -> showDirectory.resolve(kodiName + EXTENSION_SEPARATOR + extension))
                .toList();
    }

    /**
     * @param season season, or {@code null} for the image of all seasons
     */
    public static List<Path> seasonArtworkCandidates(Path showDirectory, Integer season, String kodiName) {
        String prefix = seasonPrefix(season);
        return CompanionFiles.ARTWORK_EXTENSIONS.stream()
                .map(extension -> showDirectory.resolve(prefix + ARTWORK_SEPARATOR + kodiName + EXTENSION_SEPARATOR
                        + extension))
                .toList();
    }

    /**
     * Existing images of a season in the TV show folder, of every type Kodi knows, e.g. {@code season01-poster.jpg}
     * or {@code season-specials-banner.png}.
     *
     * @throws IOException if the folder cannot be read
     */
    public static List<Path> seasonArtworkFiles(Path showDirectory, int season) throws IOException {
        String prefix = (seasonPrefix(season) + ARTWORK_SEPARATOR).toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.list(showDirectory)) {
            return files.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> isSeasonArtwork(file.getFileName().toString().toLowerCase(Locale.ROOT), prefix))
                    .sorted()
                    .toList();
        }
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

    private static boolean isSeasonArtwork(String fileName, String prefix) {
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        return fileName.startsWith(prefix) && extensionStart > prefix.length()
                && CompanionFiles.ARTWORK_EXTENSIONS.contains(fileName.substring(extensionStart + 1));
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
