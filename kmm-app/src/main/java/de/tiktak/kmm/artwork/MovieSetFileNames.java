package de.tiktak.kmm.artwork;

import java.util.regex.Pattern;

/**
 * Names of the set images derived from the set title. Like Ember, the flat scheme replaces characters of a
 * Windows file name that are not allowed by {@code -}; KMM names the set folder like Kodi
 * ({@code CUtil::MakeLegalFileName}), so that Kodi finds it.
 */
final class MovieSetFileNames {

    private static final Pattern FLAT_INVALID = Pattern.compile("[\"<>|:*?\\\\/\\x00-\\x1F]");
    private static final String FLAT_REPLACEMENT = "-";
    private static final Pattern KODI_INVALID = Pattern.compile("[/\\\\?:*\"<>|]");
    private static final String KODI_REPLACEMENT = "_";
    private static final Pattern KODI_TRAILING = Pattern.compile("[. ]+$");

    private MovieSetFileNames() {
    }

    /** Start of the file names in the flat scheme, e.g. {@code Star Wars- Filmreihe} for {@code -poster.jpg}. */
    static String flat(String title) {
        return FLAT_INVALID.matcher(title).replaceAll(FLAT_REPLACEMENT);
    }

    /** Name of the set folder, e.g. {@code Star Wars_ Filmreihe}. */
    static String kodiFolder(String title) {
        return KODI_TRAILING.matcher(KODI_INVALID.matcher(title).replaceAll(KODI_REPLACEMENT)).replaceAll("");
    }
}
