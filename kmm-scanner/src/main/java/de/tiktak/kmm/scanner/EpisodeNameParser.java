package de.tiktak.kmm.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes season and episode in the file name like Kodi's default rules:
 * <ul>
 * <li>{@code S01E02}, {@code s1e2}, {@code S1970E01}, multiple {@code S01E01E02} or {@code S01E01-E02}</li>
 * <li>{@code 1x02}, multiple {@code 1x02x03}</li>
 * <li>short form at the end {@code …-102} (season 1, episode 2) or {@code …-1012} (season 10, episode 12);
 * four-digit numbers that look like a year do not count as an episode</li>
 * </ul>
 */
public class EpisodeNameParser {

    private static final Pattern SEASON_EPISODE =
            Pattern.compile("(?i)(?<![a-z0-9])s(\\d{1,4})[ ._-]*e(\\d{1,4})((?:[ ._-]*e\\d{1,4})*)");
    private static final Pattern ADDITIONAL_E = Pattern.compile("(?i)e(\\d{1,4})");
    private static final Pattern CROSS = Pattern.compile("(?i)(?<![0-9])(\\d{1,2})x(\\d{2,3})((?:x\\d{2,3})*)(?![0-9])");
    private static final Pattern ADDITIONAL_X = Pattern.compile("(?i)x(\\d{2,3})");
    private static final Pattern SHORT_FORM = Pattern.compile("(?<=[._ -])(\\d{1,2})(\\d{2})(?=$|[._ -])");
    private static final Pattern YEAR_LIKE = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern LEADING_SEPARATORS = Pattern.compile("^[ ._-]+");

    public Optional<EpisodeNumber> parse(String nameWithoutExtension) {
        return withMarkers(nameWithoutExtension, SEASON_EPISODE, ADDITIONAL_E)
                .or(() -> withMarkers(nameWithoutExtension, CROSS, ADDITIONAL_X))
                .or(() -> shortForm(nameWithoutExtension));
    }

    /**
     * Text after the episode number as suggested title, e.g. "Der Fremde" from {@code ALF - S01E02 - Der Fremde};
     * only for {@code S01E02} and {@code 1x02}.
     *
     * @return empty if there is no text after the number
     */
    public Optional<String> titleAfterNumber(String nameWithoutExtension) {
        for (Pattern pattern : List.of(SEASON_EPISODE, CROSS)) {
            Matcher matcher = pattern.matcher(nameWithoutExtension);
            if (matcher.find()) {
                String rest = LEADING_SEPARATORS.matcher(nameWithoutExtension.substring(matcher.end())).replaceFirst("");
                return rest.isBlank() ? Optional.empty() : Optional.of(rest.strip());
            }
        }
        return Optional.empty();
    }

    private static Optional<EpisodeNumber> withMarkers(String name, Pattern pattern, Pattern additional) {
        Matcher matcher = pattern.matcher(name);
        if (!matcher.find()) {
            return Optional.empty();
        }
        List<Integer> episodes = new ArrayList<>();
        episodes.add(Integer.valueOf(matcher.group(2)));
        Matcher more = additional.matcher(matcher.group(3));
        while (more.find()) {
            int episode = Integer.parseInt(more.group(1));
            if (!episodes.contains(episode)) {
                episodes.add(episode);
            }
        }
        return Optional.of(new EpisodeNumber(Integer.parseInt(matcher.group(1)), episodes));
    }

    /** The last matching number wins because release names often contain codecs like {@code x264} at the front. */
    private static Optional<EpisodeNumber> shortForm(String name) {
        Matcher matcher = SHORT_FORM.matcher(name);
        EpisodeNumber found = null;
        while (matcher.find()) {
            if (!YEAR_LIKE.matcher(matcher.group()).matches()) {
                found = new EpisodeNumber(Integer.parseInt(matcher.group(1)),
                        List.of(Integer.valueOf(matcher.group(2))));
            }
        }
        return Optional.ofNullable(found);
    }
}
