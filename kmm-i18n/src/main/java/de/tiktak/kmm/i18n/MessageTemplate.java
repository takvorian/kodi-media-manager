package de.tiktak.kmm.i18n;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The subset of ICU MessageFormat that the user interface (messageformat.js) understands as well, so one message
 * text works on both sides:
 * <ul>
 *   <li>{@code {name}} inserts an argument,</li>
 *   <li>{@code {count, plural, =0 {none} one {# movie} other {# movies}}} chooses by number ({@code #} is the
 *       number),</li>
 *   <li>{@code {kind, select, movie {…} other {…}}} chooses by value,</li>
 *   <li>an apostrophe quotes special characters: {@code '{title}'} is literal text, {@code ''} is one apostrophe.</li>
 * </ul>
 * German and English share the plural rule "one for 1, other for everything else".
 */
final class MessageTemplate {

    private static final String PLURAL = "plural";
    private static final String SELECT = "select";
    private static final String OTHER = "other";
    private static final String ONE = "one";
    private static final char QUOTE = '\'';
    private static final char OPEN = '{';
    private static final char CLOSE = '}';
    private static final char POUND = '#';
    private static final char COMMA = ',';
    private static final char EXACT = '=';

    private final List<Part> parts;

    private MessageTemplate(List<Part> parts) {
        this.parts = parts;
    }

    /**
     * @throws IllegalArgumentException if the pattern is malformed, e.g. a brace is not closed
     */
    static MessageTemplate parse(String pattern) {
        Parser parser = new Parser(pattern);
        List<Part> parts = parser.message(false);
        if (!parser.atEnd()) {
            throw parser.error("unexpected '}'");
        }
        return new MessageTemplate(parts);
    }

    /**
     * @param args     named arguments; missing arguments stay visible as {@code {name}}
     * @param nested   renders arguments that are texts themselves
     */
    String format(Map<String, Object> args, Function<LocalizedText, String> nested) {
        StringBuilder out = new StringBuilder();
        new Formatter(args, nested, out).write(parts, null);
        return out.toString();
    }

    private sealed interface Part permits Literal, Argument, Choice, Pound {
    }

    private record Literal(String text) implements Part {
    }

    private record Argument(String name) implements Part {
    }

    /** Plural or select with its alternatives in source order. */
    private record Choice(String name, boolean plural, Map<String, List<Part>> options) implements Part {
    }

    /** The number of the enclosing plural. */
    private record Pound() implements Part {
    }

    private static final class Parser {

        private final String pattern;
        private int position;

        Parser(String pattern) {
            this.pattern = pattern;
        }

        boolean atEnd() {
            return position >= pattern.length();
        }

        /** Reads up to the end or up to the closing brace of a sub-message (which is not consumed). */
        List<Part> message(boolean inPlural) {
            List<Part> parts = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            while (!atEnd() && current() != CLOSE) {
                char character = current();
                if (character == QUOTE) {
                    quoted(text, inPlural);
                } else if (character == OPEN) {
                    flush(text, parts);
                    parts.add(argument());
                } else if (character == POUND && inPlural) {
                    flush(text, parts);
                    parts.add(new Pound());
                    position++;
                } else {
                    text.append(character);
                    position++;
                }
            }
            flush(text, parts);
            return parts;
        }

        private void quoted(StringBuilder text, boolean inPlural) {
            position++;
            if (!atEnd() && current() == QUOTE) {
                text.append(QUOTE);
                position++;
                return;
            }
            if (atEnd() || !isSpecial(current(), inPlural)) {
                text.append(QUOTE);
                return;
            }
            while (!atEnd()) {
                char character = current();
                position++;
                if (character != QUOTE) {
                    text.append(character);
                } else if (!atEnd() && current() == QUOTE) {
                    text.append(QUOTE);
                    position++;
                } else {
                    return;
                }
            }
        }

        private static boolean isSpecial(char character, boolean inPlural) {
            return character == OPEN || character == CLOSE || (inPlural && character == POUND);
        }

        private Part argument() {
            position++;
            String name = token(COMMA, CLOSE);
            if (consume(CLOSE)) {
                return new Argument(name);
            }
            expect(COMMA);
            String type = token(COMMA, CLOSE);
            if (!PLURAL.equals(type) && !SELECT.equals(type)) {
                throw error("unsupported argument type '" + type + "'");
            }
            expect(COMMA);
            Map<String, List<Part>> options = new LinkedHashMap<>();
            skipWhitespace();
            while (!atEnd() && current() != CLOSE) {
                String selector = selector();
                skipWhitespace();
                expect(OPEN);
                options.put(selector, message(PLURAL.equals(type)));
                expect(CLOSE);
                skipWhitespace();
            }
            expect(CLOSE);
            if (!options.containsKey(OTHER)) {
                throw error("'" + name + "' needs an 'other' option");
            }
            return new Choice(name, PLURAL.equals(type), options);
        }

        private String token(char... terminators) {
            int start = position;
            while (!atEnd() && !isAny(current(), terminators)) {
                position++;
            }
            if (atEnd()) {
                throw error("unclosed '{'");
            }
            String token = pattern.substring(start, position).strip();
            if (token.isEmpty()) {
                throw error("empty argument");
            }
            return token;
        }

        private String selector() {
            int start = position;
            while (!atEnd() && !Character.isWhitespace(current()) && current() != OPEN) {
                position++;
            }
            return pattern.substring(start, position);
        }

        private static boolean isAny(char character, char... candidates) {
            for (char candidate : candidates) {
                if (character == candidate) {
                    return true;
                }
            }
            return false;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(current())) {
                position++;
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && current() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private char current() {
            return pattern.charAt(position);
        }

        private static void flush(StringBuilder text, List<Part> parts) {
            if (!text.isEmpty()) {
                parts.add(new Literal(text.toString()));
                text.setLength(0);
            }
        }

        IllegalArgumentException error(String problem) {
            return new IllegalArgumentException(
                    "Invalid message at position %d: %s in \"%s\"".formatted(position, problem, pattern));
        }
    }

    private record Formatter(Map<String, Object> args, Function<LocalizedText, String> nested, StringBuilder out) {

        void write(List<Part> parts, Object pluralValue) {
            for (Part part : parts) {
                switch (part) {
                    case Literal literal -> out.append(literal.text());
                    case Argument argument -> out.append(argument(argument.name()));
                    case Pound pound -> out.append(pluralValue == null ? POUND : String.valueOf(pluralValue));
                    case Choice choice -> writeChoice(choice);
                }
            }
        }

        private String argument(String name) {
            if (!args.containsKey(name)) {
                return OPEN + name + CLOSE;
            }
            Object value = args.get(name);
            if (value instanceof LocalizedText text) {
                return nested.apply(text);
            }
            return value == null ? "" : value.toString();
        }

        private void writeChoice(Choice choice) {
            Object value = args.get(choice.name());
            List<Part> option = choice.plural() ? pluralOption(choice, value) : selectOption(choice, value);
            write(option, choice.plural() ? value : null);
        }

        private static List<Part> pluralOption(Choice choice, Object value) {
            Long number = asWholeNumber(value);
            if (number != null) {
                List<Part> exact = choice.options().get(String.valueOf(EXACT) + number);
                if (exact != null) {
                    return exact;
                }
                if (number == 1 && choice.options().containsKey(ONE)) {
                    return choice.options().get(ONE);
                }
            }
            return choice.options().get(OTHER);
        }

        private static List<Part> selectOption(Choice choice, Object value) {
            return choice.options().getOrDefault(String.valueOf(value), choice.options().get(OTHER));
        }

        private static Long asWholeNumber(Object value) {
            if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
                return number.longValue();
            }
            if (value instanceof String text) {
                try {
                    return Long.parseLong(text.strip());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }
}
