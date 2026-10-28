package de.tiktak.kmm.scanner.naming;

import java.util.regex.Pattern;

/**
 * Macht Texte zu gültigen Datei- und Ordnernamen unter Windows, Linux und auf Netzlaufwerken, wie Ember:
 * {@code :} wird zu {@code " -"}, verbotene Zeichen entfallen, Leerzeichen und Punkte am Ende ebenso.
 */
public final class FileNames {

    private static final Pattern COLON = Pattern.compile("\\s*:\\s*");
    private static final Pattern FORBIDDEN = Pattern.compile("[?*\"<>|\\p{Cntrl}]");
    private static final Pattern SEPARATORS = Pattern.compile("[/\\\\]");
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern TRAILING = Pattern.compile("[\\s.]+$");

    private FileNames() {
    }

    /** @return der gültige Name, leer, wenn nichts übrig bleibt */
    public static String clean(String name) {
        String cleaned = COLON.matcher(name).replaceAll(" - ");
        cleaned = FORBIDDEN.matcher(cleaned).replaceAll("");
        cleaned = SEPARATORS.matcher(cleaned).replaceAll("-");
        cleaned = SPACES.matcher(cleaned).replaceAll(" ").strip();
        return TRAILING.matcher(cleaned).replaceAll("");
    }

    /** Ein Wert darf keine Ordner trennen: {@code AC/DC} wird zu {@code AC-DC}. */
    static String withoutSeparators(String value) {
        return SEPARATORS.matcher(value).replaceAll("-");
    }
}
