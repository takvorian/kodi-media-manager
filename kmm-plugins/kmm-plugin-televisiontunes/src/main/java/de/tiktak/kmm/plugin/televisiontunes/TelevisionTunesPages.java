package de.tiktak.kmm.plugin.televisiontunes;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest Suchergebnisse und Serienseiten von TelevisionTunes. Die Seite hat kein API; ausgewertet werden die
 * Einträge der Suche ({@code data-show} mit Link auf {@code /show/…}) und die Abspiel-Aufrufe der Serienseite
 * ({@code playInBar('/uploads/audio/….mp3', Serie, Titel, Seite, …)}).
 */
final class TelevisionTunesPages {

    private static final Pattern SHOW_ENTRY = Pattern.compile(
            "data-show=\"([^\"]*)\"[^>]*>\\s*<a[^>]*href=\"(/show/[^\"]+)\"");
    private static final String JS_STRING = "'((?:\\\\.|[^'\\\\])*)'";
    private static final Pattern TRACK = Pattern.compile("playInBar\\(" + JS_STRING + "\\s*,\\s*" + JS_STRING
            + "\\s*,\\s*" + JS_STRING + "\\s*,\\s*" + JS_STRING);
    private static final String AUDIO_PATH_PREFIX = "/uploads/audio/";
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{Alnum}]+");
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(the|der|die|das)\\s+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private TelevisionTunesPages() {
    }

    /** Serien der Suchergebnisse in der Reihenfolge der Seite, ohne Doppelte. */
    static List<ShowLink> shows(String searchPage) {
        Map<String, ShowLink> shows = new LinkedHashMap<>();
        Matcher matcher = SHOW_ENTRY.matcher(searchPage);
        while (matcher.find()) {
            ShowLink show = new ShowLink(unescapeHtml(matcher.group(1)), matcher.group(2));
            shows.putIfAbsent(show.path(), show);
        }
        return List.copyOf(shows.values());
    }

    /** Titelmusik einer Serienseite in der Reihenfolge der Seite, jede Datei einmal. */
    static List<Track> tracks(String showPage) {
        Map<String, Track> tracks = new LinkedHashMap<>();
        Matcher matcher = TRACK.matcher(showPage);
        while (matcher.find()) {
            String audioPath = unescapeJs(matcher.group(1));
            if (audioPath.startsWith(AUDIO_PATH_PREFIX)) {
                tracks.putIfAbsent(audioPath, new Track(audioPath, unescapeJs(matcher.group(2)),
                        unescapeJs(matcher.group(3)), unescapeJs(matcher.group(4))));
            }
        }
        return new ArrayList<>(tracks.values());
    }

    static boolean isAudioPath(String path) {
        return path != null && path.startsWith(AUDIO_PATH_PREFIX) && !path.contains("..");
    }

    /** Vergleichbare Form eines Titels: ohne Groß-/Kleinschreibung, Akzente, Artikel am Anfang und Satzzeichen. */
    static String normalize(String title) {
        String plain = DIACRITICS.matcher(Normalizer.normalize(title, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).strip();
        return NOT_ALPHANUMERIC.matcher(LEADING_ARTICLE.matcher(plain).replaceFirst("")).replaceAll("");
    }

    private static String unescapeJs(String value) {
        return value.replaceAll("\\\\(.)", "$1");
    }

    private static String unescapeHtml(String value) {
        return value.replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
    }

    /** @param path Pfad der Serienseite, z. B. {@code /show/ALF} */
    record ShowLink(String name, String path) {
    }

    /**
     * @param audioPath Pfad der MP3-Datei
     * @param show      Serie bzw. Film
     * @param title     Bezeichnung, z. B. {@code Main Theme}
     * @param pagePath  Seite der Musik
     */
    record Track(String audioPath, String show, String title, String pagePath) {
    }
}
