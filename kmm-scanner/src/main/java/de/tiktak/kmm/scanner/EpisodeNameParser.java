package de.tiktak.kmm.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt Staffel und Episode im Dateinamen wie Kodis Standardregeln:
 * <ul>
 * <li>{@code S01E02}, {@code s1e2}, {@code S1970E01}, mehrfach {@code S01E01E02} oder {@code S01E01-E02}</li>
 * <li>{@code 1x02}, mehrfach {@code 1x02x03}</li>
 * <li>Kurzform am Ende {@code …-102} (Staffel 1, Episode 2) bzw. {@code …-1012} (Staffel 10, Episode 12);
 * vierstellige Zahlen, die wie ein Jahr aussehen, gelten nicht als Episode</li>
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
     * Text nach der Episodenangabe als Titelvorschlag, z. B. „Der Fremde“ aus
     * {@code ALF - S01E02 - Der Fremde}; nur bei {@code S01E02} und {@code 1x02}.
     *
     * @return leer, wenn hinter der Angabe kein Text steht
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

    /** Die letzte passende Zahl gewinnt, weil Release-Namen vorne oft Codecs wie {@code x264} enthalten. */
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
