package de.tiktak.kmm.artwork;

import java.util.regex.Pattern;

/**
 * Namen der Set-Bilder aus dem Settitel. Das flache Schema ersetzt wie Ember unzulässige Zeichen eines
 * Windows-Dateinamens durch {@code -}; den Set-Ordner benennt KMM wie Kodi ({@code CUtil::MakeLegalFileName}),
 * damit Kodi ihn findet.
 */
final class MovieSetFileNames {

    private static final Pattern FLAT_INVALID = Pattern.compile("[\"<>|:*?\\\\/\\x00-\\x1F]");
    private static final String FLAT_REPLACEMENT = "-";
    private static final Pattern KODI_INVALID = Pattern.compile("[/\\\\?:*\"<>|]");
    private static final String KODI_REPLACEMENT = "_";
    private static final Pattern KODI_TRAILING = Pattern.compile("[. ]+$");

    private MovieSetFileNames() {
    }

    /** Anfang der Dateinamen im flachen Schema, z. B. {@code Star Wars- Filmreihe} für {@code -poster.jpg}. */
    static String flat(String title) {
        return FLAT_INVALID.matcher(title).replaceAll(FLAT_REPLACEMENT);
    }

    /** Name des Set-Ordners, z. B. {@code Star Wars_ Filmreihe}. */
    static String kodiFolder(String title) {
        return KODI_TRAILING.matcher(KODI_INVALID.matcher(title).replaceAll(KODI_REPLACEMENT)).replaceAll("");
    }
}
