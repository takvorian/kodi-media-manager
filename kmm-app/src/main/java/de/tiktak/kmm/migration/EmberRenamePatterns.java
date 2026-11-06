package de.tiktak.kmm.migration;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates Ember's patterns ({@code $T {($Y)}}) into KMM's patterns ({@code {title}[ ({year})]}). Ember's control
 * codes without a counterpart (e.g. {@code $D}, {@code $F}, {@code $X.}, {@code $?a?b?}) cannot be translated; then
 * the previous pattern stays.
 */
final class EmberRenamePatterns {

    /** {@code $W2_S?2E?}: season and episode with number of digits, separator for several seasons, and prefixes. */
    private static final Pattern SEASON_AND_EPISODE = Pattern.compile("^\\$W(\\d)[._x]?([^?$\\d]*)\\?(\\d)([^?$]*)\\?");
    /** {@code $K2_?}: season with number of digits, separator for several seasons, and prefix. */
    private static final Pattern SEASON = Pattern.compile("^\\$K(\\d)[._x]?([^?$]*)\\?");
    /** {@code $Q2E?}: episode like {@code $K}. */
    private static final Pattern EPISODE = Pattern.compile("^\\$Q(\\d)[._x]?([^?$]*)\\?");

    private static final Map<String, String> SIMPLE = Map.ofEntries(
            Map.entry("$OO", "{originaltitle}"),
            Map.entry("$T", "{title}"),
            Map.entry("$Y", "{year}"),
            Map.entry("$O", "{originaltitle}"),
            Map.entry("$E", "{sorttitle}"),
            Map.entry("$1", "{sorttitle:first}"),
            Map.entry("$L", "{title:sort}"),
            Map.entry("$N", "{set}"),
            Map.entry("$5", "{set:sort}"),
            Map.entry("$6", "{edition}"),
            Map.entry("$I", "{imdb}"),
            Map.entry("$S", "{source}"),
            Map.entry("$R", "{resolution}"),
            Map.entry("$H", "{videocodec}"),
            Map.entry("$J", "{audiocodec}"),
            Map.entry("$A", "{channels}"),
            Map.entry("$M", "{mpaa}"),
            Map.entry("$P", "{rating}"),
            Map.entry("$G", "{genres}"),
            Map.entry("$C", "{directors}"),
            Map.entry("$U", "{countries}"),
            Map.entry("$V", "{3d}"),
            Map.entry("$Z", "{show}"),
            Map.entry("$2", "{aired}"));

    private EmberRenamePatterns() {
    }

    /** @return the translated pattern, or empty if it contains parts without a counterpart */
    static Optional<String> translate(String ember) {
        if (ember == null || ember.isBlank()) {
            return Optional.empty();
        }
        String text = ember.strip();
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '$') {
                Optional<Translated> token = token(text.substring(i));
                if (token.isEmpty()) {
                    return Optional.empty();
                }
                result.append(token.get().text());
                i += token.get().length();
                continue;
            }
            result.append(switch (c) {
                case '{' -> "[";
                case '}' -> "]";
                case '[' -> "[[";
                case ']' -> "]]";
                default -> String.valueOf(c);
            });
            i++;
        }
        return Optional.of(result.toString());
    }

    private static Optional<Translated> token(String text) {
        Matcher both = SEASON_AND_EPISODE.matcher(text);
        if (both.find()) {
            return Optional.of(new Translated(both.group(2) + number("season", both.group(1)) + both.group(4)
                    + number("episode", both.group(3)), both.end()));
        }
        Matcher season = SEASON.matcher(text);
        if (season.find()) {
            return Optional.of(new Translated(season.group(2) + number("season", season.group(1)), season.end()));
        }
        Matcher episode = EPISODE.matcher(text);
        if (episode.find()) {
            return Optional.of(new Translated(episode.group(2) + number("episode", episode.group(1)), episode.end()));
        }
        return SIMPLE.entrySet().stream()
                .filter(entry -> text.startsWith(entry.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(entry -> new Translated(entry.getValue(), entry.getKey().length()));
    }

    private static String number(String name, String width) {
        return "{" + name + (width.equals("0") || width.equals("1") ? "" : ":" + width) + "}";
    }

    private record Translated(String text, int length) {
    }
}
