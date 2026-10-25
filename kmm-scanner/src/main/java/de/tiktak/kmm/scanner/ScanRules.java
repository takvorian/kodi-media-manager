package de.tiktak.kmm.scanner;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regeln, welche Dateien als Filme gelten und welche Ordner übersprungen werden.
 *
 * @param videoExtensions       Dateiendungen ohne Punkt, Groß-/Kleinschreibung egal
 * @param ignoredDirectoryNames Ordner, die samt Inhalt ignoriert werden (Extras, Trailer, Kodi-Artwork …)
 */
public record ScanRules(Set<String> videoExtensions, Set<String> ignoredDirectoryNames) {

    private static final Set<String> DEFAULT_VIDEO_EXTENSIONS = Set.of(
            "avi", "divx", "flv", "img", "iso", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "ogm",
            "rmvb", "ts", "webm", "wmv");

    private static final Set<String> DEFAULT_IGNORED_DIRECTORIES = Set.of(
            "extras", "featurettes", "behind the scenes", "deleted scenes", "interviews", "scenes", "shorts",
            "trailers", "sample", "samples", "other", "extrafanart", "extrathumbs", ".actors", "@eadir",
            "$recycle.bin", "system volume information");

    public ScanRules {
        videoExtensions = lowerCase(videoExtensions);
        ignoredDirectoryNames = lowerCase(ignoredDirectoryNames);
    }

    public static ScanRules defaults() {
        return new ScanRules(DEFAULT_VIDEO_EXTENSIONS, DEFAULT_IGNORED_DIRECTORIES);
    }

    public boolean isVideoExtension(String extension) {
        return videoExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    public boolean isIgnoredDirectory(String directoryName) {
        return ignoredDirectoryNames.contains(directoryName.toLowerCase(Locale.ROOT));
    }

    private static Set<String> lowerCase(Set<String> values) {
        return values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
