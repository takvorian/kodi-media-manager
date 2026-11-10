package de.tiktak.kmm.scanner.naming;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Which files belong to a video file by their name, as in Ember and Kodi: the companion files (NFO, images, subtitles,
 * further parts …) start with the name of the video file without its extension, followed by a dot, hyphen, underscore
 * or space. Upper and lower case do not matter.
 */
public final class CompanionNames {

    private static final String NAME_SEPARATORS = ".-_ ";
    private static final char EXTENSION_SEPARATOR = '.';

    private CompanionNames() {
    }

    /** @return whether the file name is the base name itself or starts with it followed by a separator */
    public static boolean belongsTo(String fileName, String baseName) {
        return fileName.equals(baseName) || (fileName.length() > baseName.length()
                && fileName.regionMatches(true, 0, baseName, 0, baseName.length())
                && NAME_SEPARATORS.indexOf(fileName.charAt(baseName.length())) >= 0);
    }

    /**
     * Like {@link #belongsTo(String, String)}, but a file that also belongs to a longer base name of another video in
     * the same folder ({@code Heat.mkv} and {@code Heat 2.mkv}) belongs to that one only.
     *
     * @param otherBaseNames base names of the other videos in the same folder
     */
    public static boolean belongsOnlyTo(String fileName, String baseName, Collection<String> otherBaseNames) {
        return belongsTo(fileName, baseName) && otherBaseNames.stream()
                .noneMatch(other -> other.length() > baseName.length() && belongsTo(fileName, other));
    }

    /** File name without its extension, e.g. {@code Heat} for {@code Heat.mkv}. */
    public static String baseName(Path file) {
        String name = file.getFileName().toString();
        int extension = name.lastIndexOf(EXTENSION_SEPARATOR);
        return extension > 0 ? name.substring(0, extension) : name;
    }
}
