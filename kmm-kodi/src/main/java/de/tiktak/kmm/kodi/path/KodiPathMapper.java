package de.tiktak.kmm.kodi.path;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Translates paths as KMM sees them into paths as Kodi sees them. Windows paths are compared case-insensitively,
 * POSIX paths exactly. If several mappings match, the one with the longest prefix wins.
 */
public class KodiPathMapper {

    private static final char POSIX_SEPARATOR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';
    private static final String URL_SCHEME_SEPARATOR = "://";
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private final List<KodiPathMapping> mappingsByPrefixLengthDescending;

    public KodiPathMapper(List<KodiPathMapping> mappings) {
        this.mappingsByPrefixLengthDescending = mappings.stream()
                .sorted(Comparator.comparingInt((KodiPathMapping mapping) -> localPrefix(mapping).length()).reversed())
                .toList();
    }

    /**
     * @return the path as Kodi sees it, or empty if no mapping matches
     */
    public Optional<String> toKodiPath(String localPath) {
        String normalized = normalize(localPath);
        return mappingsByPrefixLengthDescending.stream()
                .filter(mapping -> matches(normalized, mapping))
                .findFirst()
                .map(mapping -> apply(mapping, normalized));
    }

    /**
     * @return the directory as Kodi sees it with a trailing separator, as Kodi expects it
     */
    public Optional<String> toKodiDirectory(String localDirectory) {
        return toKodiPath(localDirectory).map(path -> {
            char separator = separatorOf(path);
            return path.charAt(path.length() - 1) == separator ? path : path + separator;
        });
    }

    private static boolean matches(String normalizedPath, KodiPathMapping mapping) {
        String prefix = localPrefix(mapping);
        boolean ignoreCase = isWindowsPath(mapping.localPath());
        if (normalizedPath.length() == prefix.length()) {
            return normalizedPath.regionMatches(ignoreCase, 0, prefix, 0, prefix.length());
        }
        return normalizedPath.length() > prefix.length()
                && normalizedPath.charAt(prefix.length()) == POSIX_SEPARATOR
                && normalizedPath.regionMatches(ignoreCase, 0, prefix, 0, prefix.length());
    }

    private static String apply(KodiPathMapping mapping, String normalizedPath) {
        String remainder = normalizedPath.substring(localPrefix(mapping).length());
        String target = mapping.kodiPath();
        return stripTrailingSeparators(target) + remainder.replace(POSIX_SEPARATOR, separatorOf(target));
    }

    private static char separatorOf(String kodiPath) {
        if (kodiPath.contains(URL_SCHEME_SEPARATOR) || kodiPath.charAt(0) == POSIX_SEPARATOR) {
            return POSIX_SEPARATOR;
        }
        return isWindowsPath(kodiPath) ? WINDOWS_SEPARATOR : POSIX_SEPARATOR;
    }

    private static boolean isWindowsPath(String path) {
        return path.indexOf(WINDOWS_SEPARATOR) >= 0 || WINDOWS_DRIVE.matcher(path).matches();
    }

    private static String localPrefix(KodiPathMapping mapping) {
        return stripTrailingSeparators(normalize(mapping.localPath()));
    }

    private static String normalize(String path) {
        return path.strip().replace(WINDOWS_SEPARATOR, POSIX_SEPARATOR);
    }

    private static String stripTrailingSeparators(String path) {
        int end = path.length();
        while (end > 0 && (path.charAt(end - 1) == POSIX_SEPARATOR || path.charAt(end - 1) == WINDOWS_SEPARATOR)) {
            end--;
        }
        return path.substring(0, end);
    }
}
