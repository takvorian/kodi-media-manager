package de.tiktak.kmm.kodi.rpc;

import java.util.Arrays;
import java.util.List;

/**
 * Compares file paths the way Kodi stores them in its library. Depending on the source, Kodi uses {@code /}
 * (smb://, nfs://, Linux) or {@code \} (Windows drives).
 */
final class KodiFiles {

    /** Kodi combines multi-part movies as {@code stack://part1 , part2}. */
    private static final String STACK_PREFIX = "stack://";
    private static final String STACK_SEPARATOR = " , ";
    /** Kodi doubles commas in file names within a stack. */
    private static final String ESCAPED_COMMA = ",,";

    private KodiFiles() {
    }

    /**
     * @return the directory of the file with a trailing separator, as Kodi stores it as path
     */
    static String directoryOf(String file) {
        int separator = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        if (separator < 0) {
            throw new IllegalArgumentException("not an absolute file path: " + file);
        }
        return file.substring(0, separator + 1);
    }

    /**
     * @param kodiFile file from Kodi's library, possibly a stack
     * @return the individual files, all parts for a stack
     */
    static List<String> parts(String kodiFile) {
        if (kodiFile.regionMatches(true, 0, STACK_PREFIX, 0, STACK_PREFIX.length())) {
            return Arrays.stream(kodiFile.substring(STACK_PREFIX.length()).split(STACK_SEPARATOR))
                    .map(part -> part.replace(ESCAPED_COMMA, ","))
                    .toList();
        }
        return List.of(kodiFile);
    }

    /**
     * @param kodiFile file from Kodi's library, possibly a stack
     * @param file     the file looked for; for multi-part movies the first part
     */
    static boolean matches(String kodiFile, String file) {
        return parts(kodiFile).stream().anyMatch(part -> part.equalsIgnoreCase(file));
    }
}
