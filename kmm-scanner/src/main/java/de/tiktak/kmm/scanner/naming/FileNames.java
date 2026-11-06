package de.tiktak.kmm.scanner.naming;

import java.util.regex.Pattern;

/**
 * Turns texts into valid file and folder names on Windows, Linux and network drives, like Ember: {@code :} becomes
 * {@code " -"}, forbidden characters are dropped, as are trailing spaces and dots.
 */
public final class FileNames {

    private static final Pattern COLON = Pattern.compile("\\s*:\\s*");
    private static final Pattern FORBIDDEN = Pattern.compile("[?*\"<>|\\p{Cntrl}]");
    private static final Pattern SEPARATORS = Pattern.compile("[/\\\\]");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern TRAILING = Pattern.compile("[\\s.]+$");

    private FileNames() {
    }

    /** @return the valid name, empty if nothing is left */
    public static String clean(String name) {
        String cleaned = COLON.matcher(name).replaceAll(" - ");
        cleaned = FORBIDDEN.matcher(cleaned).replaceAll("");
        cleaned = SEPARATORS.matcher(cleaned).replaceAll("-");
        cleaned = SPACES.matcher(cleaned).replaceAll(" ").strip();
        return TRAILING.matcher(cleaned).replaceAll("");
    }

    /** A value must not separate folders: {@code AC/DC} becomes {@code AC-DC}. */
    static String withoutSeparators(String value) {
        return SEPARATORS.matcher(value).replaceAll("-");
    }
}
