package de.tiktak.kmm.plugin.televisiontunes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads search results and show pages of TelevisionTunes. The site has no API; KMM evaluates the entries of the
 * search ({@code data-show} with a link to {@code /show/…}) and the play calls of the show page
 * ({@code playInBar('/uploads/audio/….mp3', show, title, page, …)}).
 */
final class TelevisionTunesPages {

    private static final Pattern SHOW_ENTRY = Pattern.compile(
            "data-show=\"([^\"]*)\"[^>]*>\\s*<a[^>]*href=\"(/show/[^\"]+)\"");
    private static final String JS_STRING = "'((?:\\\\.|[^'\\\\])*)'";
    private static final Pattern TRACK = Pattern.compile("playInBar\\(" + JS_STRING + "\\s*,\\s*" + JS_STRING
            + "\\s*,\\s*" + JS_STRING + "\\s*,\\s*" + JS_STRING);
    private static final String AUDIO_PATH_PREFIX = "/uploads/audio/";

    private TelevisionTunesPages() {
    }

    /** Shows of the search results in the order of the page, without duplicates. */
    static List<ShowLink> shows(String searchPage) {
        Map<String, ShowLink> shows = new LinkedHashMap<>();
        Matcher matcher = SHOW_ENTRY.matcher(searchPage);
        while (matcher.find()) {
            ShowLink show = new ShowLink(unescapeHtml(matcher.group(1)), matcher.group(2));
            shows.putIfAbsent(show.path(), show);
        }
        return List.copyOf(shows.values());
    }

    /** Theme music of a show page in the order of the page, each file once. */
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

    private static String unescapeJs(String value) {
        return value.replaceAll("\\\\(.)", "$1");
    }

    private static String unescapeHtml(String value) {
        return value.replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
    }

    /** @param path path of the show page, e.g. {@code /show/ALF} */
    record ShowLink(String name, String path) {
    }

    /**
     * @param audioPath path of the MP3 file
     * @param show      TV show or movie
     * @param title     name, e.g. {@code Main Theme}
     * @param pagePath  page of the music
     */
    record Track(String audioPath, String show, String title, String pagePath) {
    }
}
