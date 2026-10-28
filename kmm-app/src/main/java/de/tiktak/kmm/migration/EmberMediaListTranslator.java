package de.tiktak.kmm.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tiktak.kmm.filter.FilterOperator;
import de.tiktak.kmm.movie.MovieFilter;
import de.tiktak.kmm.movie.MovieFilterField;

/**
 * Übersetzt eigene Filmlisten aus Embers Filter-Editor in Filter. Ember speichert freies SQL; übersetzt werden nur
 * einfache, mit {@code AND} verknüpfte Bedingungen, die KMM genauso abfragen kann. Alles andere bleibt unübersetzt.
 */
final class EmberMediaListTranslator {

    private static final Pattern WHERE = Pattern.compile("(?is)\\bwhere\\b(.+)$");
    private static final Pattern AND = Pattern.compile("(?i)\\s+and\\s+");
    private static final String COLUMN = "(?:movielist\\.)?\"?(\\w+)\"?";
    private static final Pattern IS_NULL = Pattern.compile("(?i)" + COLUMN + "\\s+is\\s+null");
    private static final Pattern IS_NOT_NULL = Pattern.compile("(?i)" + COLUMN + "\\s+is\\s+not\\s+null");
    private static final Pattern COMPARISON = Pattern.compile("(?i)" + COLUMN + "\\s*(=|>|<|>=|<=)\\s*'?(\\d+)'?");
    private static final String PLAY_COUNT = "playcount";
    private static final String WATCHED = "true";
    private static final String UNWATCHED = "false";
    /** Vergleiche der Wiedergaben, die „gesehen“ bzw. „nicht gesehen“ bedeuten. */
    private static final Map<String, String> PLAY_COUNT_COMPARISONS = Map.of(
            "=0", UNWATCHED, "<1", UNWATCHED, "<=0", UNWATCHED, ">0", WATCHED, ">=1", WATCHED);

    private EmberMediaListTranslator() {
    }

    /** @return der Filter oder leer, wenn die Liste Bedingungen enthält, die KMM nicht übersetzen kann */
    static Optional<MovieFilter> translate(String sql) {
        Matcher where = WHERE.matcher(sql.strip().replaceAll(";\\s*$", ""));
        if (!where.find()) {
            return Optional.of(MovieFilter.ALL);
        }
        List<MovieFilter.Condition> conditions = new ArrayList<>();
        for (String part : AND.split(where.group(1).strip())) {
            Optional<MovieFilter.Condition> condition = condition(stripParentheses(part.strip()));
            if (condition.isEmpty()) {
                return Optional.empty();
            }
            conditions.add(condition.get());
        }
        return Optional.of(new MovieFilter(null, true, conditions));
    }

    private static Optional<MovieFilter.Condition> condition(String part) {
        Matcher isNull = IS_NULL.matcher(part);
        if (isNull.matches() && isPlayCount(isNull.group(1))) {
            return Optional.of(watched(UNWATCHED));
        }
        Matcher isNotNull = IS_NOT_NULL.matcher(part);
        if (isNotNull.matches() && isPlayCount(isNotNull.group(1))) {
            return Optional.of(watched(WATCHED));
        }
        Matcher comparison = COMPARISON.matcher(part);
        if (comparison.matches() && isPlayCount(comparison.group(1))) {
            return Optional.ofNullable(PLAY_COUNT_COMPARISONS.get(comparison.group(2) + comparison.group(3)))
                    .map(EmberMediaListTranslator::watched);
        }
        return Optional.empty();
    }

    private static MovieFilter.Condition watched(String value) {
        return new MovieFilter.Condition(MovieFilterField.WATCHED, FilterOperator.IS, value);
    }

    private static boolean isPlayCount(String column) {
        return column.toLowerCase(Locale.ROOT).equals(PLAY_COUNT);
    }

    private static String stripParentheses(String part) {
        String stripped = part;
        while (stripped.startsWith("(") && stripped.endsWith(")")) {
            stripped = stripped.substring(1, stripped.length() - 1).strip();
        }
        return stripped;
    }
}
