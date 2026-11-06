package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rules for which files count as movies and which folders are skipped.
 *
 * @param videoExtensions       file extensions without dot, case-insensitive
 * @param ignoredDirectoryNames folders that are ignored with their content (extras, trailers, Kodi artwork …)
 * @param excludedDirectories   absolute folders that are ignored with their content
 */
public record ScanRules(Set<String> videoExtensions, Set<String> ignoredDirectoryNames,
        Set<Path> excludedDirectories) {

    public static final Set<String> DEFAULT_VIDEO_EXTENSIONS = Set.of(
            "avi", "divx", "flv", "img", "iso", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "ogm",
            "rmvb", "ts", "webm", "wmv");

    public static final Set<String> DEFAULT_IGNORED_DIRECTORIES = Set.of(
            "extras", "featurettes", "behind the scenes", "deleted scenes", "interviews", "scenes", "shorts",
            "trailers", "sample", "samples", "other", "extrafanart", "extrathumbs", ".actors", "@eadir",
            "$recycle.bin", "system volume information");

    public ScanRules {
        videoExtensions = lowerCase(videoExtensions);
        ignoredDirectoryNames = lowerCase(ignoredDirectoryNames);
        excludedDirectories = excludedDirectories.stream()
                .map(directory -> directory.toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
    }

    public ScanRules(Set<String> videoExtensions, Set<String> ignoredDirectoryNames) {
        this(videoExtensions, ignoredDirectoryNames, Set.of());
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

    /** Whether the folder is an excluded folder or lies in one. */
    public boolean isExcludedDirectory(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        return excludedDirectories.stream().anyMatch(normalized::startsWith);
    }

    private static Set<String> lowerCase(Set<String> values) {
        return values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
