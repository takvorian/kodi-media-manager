package de.tiktak.kmm.migration.ember.path;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Übersetzt in Ember gespeicherte Windows-Pfade (Laufwerk oder UNC) in Pfade des Zielsystems.
 * Der Vergleich ignoriert Groß-/Kleinschreibung und Trennzeichen, weil Ember unter Windows lief.
 * Bei mehreren passenden Regeln gewinnt die mit dem längsten Präfix.
 */
public class PathMapper {

    private static final char POSIX_SEPARATOR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';
    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private final List<PathMappingRule> rulesByPrefixLengthDescending;

    public PathMapper(List<PathMappingRule> rules) {
        this.rulesByPrefixLengthDescending = rules.stream()
                .sorted(Comparator.comparingInt((PathMappingRule rule) -> normalizedPrefix(rule).length()).reversed())
                .toList();
    }

    /**
     * @return den Zielpfad oder leer, wenn keine Regel auf den Pfad passt
     */
    public Optional<String> map(String emberPath) {
        String normalizedPath = normalize(emberPath);
        return rulesByPrefixLengthDescending.stream()
                .filter(rule -> matches(normalizedPath, normalizedPrefix(rule)))
                .findFirst()
                .map(rule -> applyRule(rule, normalizedPath));
    }

    private static boolean matches(String normalizedPath, String normalizedPrefix) {
        return normalizedPath.equalsIgnoreCase(normalizedPrefix)
                || startsWithIgnoreCase(normalizedPath, normalizedPrefix + POSIX_SEPARATOR);
    }

    private static String applyRule(PathMappingRule rule, String normalizedPath) {
        String remainder = normalizedPath.substring(normalizedPrefix(rule).length());
        String target = rule.targetPrefix();
        char targetSeparator = usesWindowsSeparator(target) ? WINDOWS_SEPARATOR : POSIX_SEPARATOR;
        String targetBase = stripTrailingSeparators(target);
        if (targetBase.isEmpty() && remainder.isEmpty()) {
            return String.valueOf(targetSeparator);
        }
        return targetBase + remainder.replace(POSIX_SEPARATOR, targetSeparator);
    }

    private static String normalizedPrefix(PathMappingRule rule) {
        return stripTrailingSeparators(normalize(rule.sourcePrefix()));
    }

    private static String normalize(String path) {
        return path.strip().replace(WINDOWS_SEPARATOR, POSIX_SEPARATOR);
    }

    private static boolean usesWindowsSeparator(String path) {
        return path.indexOf(WINDOWS_SEPARATOR) >= 0 || WINDOWS_DRIVE.matcher(path).matches();
    }

    private static String stripTrailingSeparators(String path) {
        int end = path.length();
        while (end > 0 && isSeparator(path.charAt(end - 1))) {
            end--;
        }
        return path.substring(0, end);
    }

    private static boolean isSeparator(char character) {
        return character == POSIX_SEPARATOR || character == WINDOWS_SEPARATOR;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
