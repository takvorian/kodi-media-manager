package de.tiktak.kmm.scanner.naming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Formate eines Platzhalters, mehrere hintereinander durch {@code :} getrennt ({@code {title:sort:first}}):
 * <ul>
 * <li>eine Zahl: Ziffernfolgen auf so viele Stellen mit Nullen auffüllen ({@code {season:2}} → {@code 01})</li>
 * <li>{@code first}: erster Buchstabe groß, Ziffern und Zeichen als {@code #}</li>
 * <li>{@code sort}: führenden Artikel nach hinten ({@code The Matrix} → {@code Matrix, The})</li>
 * <li>{@code lower}, {@code upper}: Klein- bzw. Großschreibung</li>
 * </ul>
 */
final class NameFormat {

    static final NameFormat NONE = new NameFormat(List.of());

    /** Artikel wie Embers Standard-Sortierwörter. */
    private static final List<String> ARTICLES = List.of("the", "a", "an", "der", "die", "das");
    private static final Pattern PADDING = Pattern.compile("[1-9]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final String NOT_A_LETTER = "#";
    private static final String SEPARATOR = ":";

    private final List<UnaryOperator<String>> steps;

    private NameFormat(List<UnaryOperator<String>> steps) {
        this.steps = steps;
    }

    /** @return das Format oder leer, wenn ein Teil unbekannt ist; ohne Text kein Format */
    static Optional<NameFormat> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.of(NONE);
        }
        List<UnaryOperator<String>> steps = new ArrayList<>();
        for (String name : text.split(SEPARATOR)) {
            Optional<UnaryOperator<String>> step = step(name.strip().toLowerCase(Locale.ROOT));
            if (step.isEmpty()) {
                return Optional.empty();
            }
            steps.add(step.get());
        }
        return Optional.of(new NameFormat(List.copyOf(steps)));
    }

    String apply(String value) {
        String result = value;
        for (UnaryOperator<String> step : steps) {
            result = step.apply(result);
        }
        return result;
    }

    private static Optional<UnaryOperator<String>> step(String name) {
        if (PADDING.matcher(name).matches()) {
            int width = Integer.parseInt(name);
            return Optional.of(value -> DIGITS.matcher(value)
                    .replaceAll(match -> "0".repeat(Math.max(0, width - match.group().length())) + match.group()));
        }
        return switch (name) {
            case "first" -> Optional.of(NameFormat::firstLetter);
            case "sort" -> Optional.of(NameFormat::articleLast);
            case "lower" -> Optional.of(value -> value.toLowerCase(Locale.ROOT));
            case "upper" -> Optional.of(value -> value.toUpperCase(Locale.ROOT));
            default -> Optional.empty();
        };
    }

    private static String firstLetter(String value) {
        int first = value.codePointAt(0);
        return Character.isLetter(first) ? new String(Character.toChars(first)).toUpperCase(Locale.ROOT)
                : NOT_A_LETTER;
    }

    private static String articleLast(String value) {
        int space = value.indexOf(' ');
        if (space <= 0 || space == value.length() - 1) {
            return value;
        }
        String article = value.substring(0, space);
        return ARTICLES.contains(article.toLowerCase(Locale.ROOT))
                ? value.substring(space + 1).strip() + ", " + article
                : value;
    }
}
