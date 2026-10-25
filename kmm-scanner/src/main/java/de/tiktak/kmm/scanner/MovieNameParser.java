package de.tiktak.kmm.scanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitet Titel und Jahr aus typischen Namen ab, z. B. {@code Heat (1995)} oder
 * {@code The.Matrix.1999.1080p.BluRay.x264}. Eine Jahreszahl am Anfang gehört zum Titel
 * ({@code 1917 (2019)}, {@code 2001 A Space Odyssey}).
 */
public class MovieNameParser {

    private static final Pattern WORD_SEPARATORS = Pattern.compile("[._]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern YEAR = Pattern.compile("(?<![0-9])(?:19|20)[0-9]{2}(?![0-9])");
    private static final Pattern RELEASE_TAG = Pattern.compile(
            "(?i)(?<![\\p{L}0-9])(?:2160p|1080p|1080i|720p|576p|480p|4k|uhd|blu-?ray|bdrip|brrip|bd25|bd50"
                    + "|dvdrip|dvd5|dvd9|webrip|web-?dl|hdtv|hdrip|x264|x265|h\\.?264|h\\.?265|hevc|avc|xvid|divx"
                    + "|remux|hdr10?|dts(?:-hd)?|truehd|atmos|ac3|aac|german|multi|proper|repack|extended"
                    + "|unrated|uncut|remastered|imax)(?![\\p{L}0-9])");
    private static final String TRIM_CHARACTERS = " -([{";

    private static final int NOT_FOUND = -1;

    public ParsedMovieName parse(String name) {
        String normalized = normalize(name);
        int yearStart = lastYearStartAfterBeginning(normalized);
        if (yearStart != NOT_FOUND) {
            Matcher year = YEAR.matcher(normalized);
            year.find(yearStart);
            return new ParsedMovieName(clean(normalized.substring(0, yearStart)), Integer.valueOf(year.group()));
        }
        return new ParsedMovieName(clean(cutAtFirstReleaseTag(normalized)), null);
    }

    private static String normalize(String name) {
        return WHITESPACE.matcher(WORD_SEPARATORS.matcher(name).replaceAll(" ")).replaceAll(" ").strip();
    }

    private static int lastYearStartAfterBeginning(String name) {
        Matcher matcher = YEAR.matcher(name);
        int lastStart = NOT_FOUND;
        while (matcher.find()) {
            if (matcher.start() > 0) {
                lastStart = matcher.start();
            }
        }
        return lastStart;
    }

    private static String cutAtFirstReleaseTag(String name) {
        Matcher tag = RELEASE_TAG.matcher(name);
        return tag.find() ? name.substring(0, tag.start()) : name;
    }

    private static String clean(String title) {
        int start = 0;
        int end = title.length();
        while (start < end && TRIM_CHARACTERS.indexOf(title.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRIM_CHARACTERS.indexOf(title.charAt(end - 1)) >= 0) {
            end--;
        }
        return title.substring(start, end);
    }
}
