package de.tiktak.kmm.kodi.rpc;

import java.util.Arrays;
import java.util.List;

/**
 * Vergleicht Dateipfade so, wie Kodi sie in seiner Bibliothek speichert. Kodi verwendet je nach
 * Quelle {@code /} (smb://, nfs://, Linux) oder {@code \} (Windows-Laufwerke).
 */
final class KodiFiles {

    /** Kodi fasst mehrteilige Filme als {@code stack://teil1 , teil2} zusammen. */
    private static final String STACK_PREFIX = "stack://";
    private static final String STACK_SEPARATOR = " , ";
    /** Kommas im Dateinamen verdoppelt Kodi innerhalb eines Stapels. */
    private static final String ESCAPED_COMMA = ",,";

    private KodiFiles() {
    }

    /**
     * @return das Verzeichnis der Datei mit abschließendem Trennzeichen, wie Kodi es als Pfad speichert
     */
    static String directoryOf(String file) {
        int separator = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        if (separator < 0) {
            throw new IllegalArgumentException("not an absolute file path: " + file);
        }
        return file.substring(0, separator + 1);
    }

    /**
     * @param kodiFile Datei aus Kodis Bibliothek, ggf. ein Stapel
     * @return die einzelnen Dateien, bei einem Stapel alle Teile
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
     * @param kodiFile Datei aus Kodis Bibliothek, ggf. ein Stapel
     * @param file     gesuchte Datei; bei mehrteiligen Filmen der erste Teil
     */
    static boolean matches(String kodiFile, String file) {
        return parts(kodiFile).stream().anyMatch(part -> part.equalsIgnoreCase(file));
    }
}
