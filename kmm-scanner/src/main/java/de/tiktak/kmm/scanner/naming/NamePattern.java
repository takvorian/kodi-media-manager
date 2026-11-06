package de.tiktak.kmm.scanner.naming;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Pattern for folder and file names, e.g. {@code {title}[ ({year})]}.
 * <ul>
 * <li>{@code {name}} is a placeholder, {@code {name:format}} a formatted one (see {@link NameFormat}).</li>
 * <li>{@code [ … ]} is an optional part: it is dropped if a placeholder in it is empty.</li>
 * <li>{@code /} and {@code \} separate folders, e.g. {@code {title:first}/{title}}.</li>
 * <li>{@code {{}, {@code }}}, {@code [[} and {@code ]]} stand for the characters themselves.</li>
 * </ul>
 * Values are cleaned before they are inserted so they do not separate folders; the result is made valid per folder
 * or file with {@link FileNames#clean}.
 */
public final class NamePattern {

    private static final char PLACEHOLDER_START = '{';
    private static final char PLACEHOLDER_END = '}';
    private static final char OPTIONAL_START = '[';
    private static final char OPTIONAL_END = ']';
    private static final char FORMAT_SEPARATOR = ':';

    private final String text;
    private final List<Part> parts;

    private NamePattern(String text, List<Part> parts) {
        this.text = text;
        this.parts = parts;
    }

    /**
     * @param placeholders the allowed placeholders
     * @throws NamePatternException if the pattern is invalid; the message is meant for the user interface
     */
    public static NamePattern parse(String text, Set<String> placeholders) {
        Objects.requireNonNull(placeholders, "placeholders must not be null");
        if (text == null || text.isBlank()) {
            throw new NamePatternException(LocalizedText.of("scanner.pattern.empty"));
        }
        return new NamePattern(text, new Parser(text, placeholders).parse());
    }

    public String text() {
        return text;
    }

    /** Whether the pattern separates folders, i.e. results in several name parts. */
    public boolean hasFolders() {
        return parts.stream().anyMatch(Part::separatesFolders);
    }

    /**
     * @param values supplies the value of a placeholder; {@code null} or empty means "not set"
     * @return the cleaned name parts, one per folder; empty parts are dropped
     */
    public List<String> render(Function<String, String> values) {
        StringBuilder raw = new StringBuilder();
        for (Part part : parts) {
            part.render(values).ifPresent(raw::append);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : raw.toString().split("\u0000", -1)) {
            String cleaned = FileNames.clean(segment);
            if (!cleaned.isEmpty()) {
                segments.add(cleaned);
            }
        }
        return List.copyOf(segments);
    }

    @Override
    public String toString() {
        return text;
    }

    /** A part of the pattern: text, placeholder, optional part or folder separator. */
    private sealed interface Part {

        /** @return the text, or empty if an optional part is dropped */
        Optional<String> render(Function<String, String> values);

        default boolean separatesFolders() {
            return false;
        }
    }

    private record Literal(String value) implements Part {

        public Optional<String> render(Function<String, String> values) {
            return Optional.of(value);
        }
    }

    /** Folder separator; a null character in the intermediate result that cannot occur in any value. */
    private record FolderSeparator() implements Part {

        public Optional<String> render(Function<String, String> values) {
            return Optional.of("\u0000");
        }

        @Override
        public boolean separatesFolders() {
            return true;
        }
    }

    private record Placeholder(String name, NameFormat format) implements Part {

        public Optional<String> render(Function<String, String> values) {
            String value = values.apply(name);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(format.apply(FileNames.withoutSeparators(value.strip())));
        }
    }

    private record OptionalPart(List<Part> parts) implements Part {

        public Optional<String> render(Function<String, String> values) {
            StringBuilder rendered = new StringBuilder();
            for (Part part : parts) {
                Optional<String> value = part.render(values);
                if (value.isEmpty()) {
                    return Optional.of("");
                }
                rendered.append(value.get());
            }
            return Optional.of(rendered.toString());
        }

        @Override
        public boolean separatesFolders() {
            return parts.stream().anyMatch(Part::separatesFolders);
        }
    }

    /** Parses the pattern text; errors name the position (starting at 1). */
    private static final class Parser {

        private final String text;
        private final Set<String> placeholders;
        private int position;

        Parser(String text, Set<String> placeholders) {
            this.text = text;
            this.placeholders = placeholders;
        }

        List<Part> parse() {
            List<Part> parts = parseParts(false);
            if (position < text.length()) {
                throw unmatched(OPTIONAL_END, OPTIONAL_START);
            }
            return List.copyOf(parts);
        }

        private List<Part> parseParts(boolean inOptional) {
            List<Part> parts = new ArrayList<>();
            StringBuilder literal = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position);
                if (isEscaped(c)) {
                    literal.append(c);
                    position += 2;
                } else if (c == PLACEHOLDER_START) {
                    flush(literal, parts);
                    parts.add(placeholder());
                } else if (c == PLACEHOLDER_END) {
                    throw unmatched(PLACEHOLDER_END, PLACEHOLDER_START);
                } else if (c == OPTIONAL_START) {
                    if (inOptional) {
                        throw error(LocalizedText.of("scanner.pattern.nestedOptional"));
                    }
                    flush(literal, parts);
                    int start = position++;
                    List<Part> optional = parseParts(true);
                    if (position >= text.length()) {
                        position = start;
                        throw unmatched(OPTIONAL_START, OPTIONAL_END);
                    }
                    position++;
                    parts.add(new OptionalPart(List.copyOf(optional)));
                } else if (c == OPTIONAL_END) {
                    if (!inOptional) {
                        throw unmatched(OPTIONAL_END, OPTIONAL_START);
                    }
                    break;
                } else if (c == '/' || c == '\\') {
                    flush(literal, parts);
                    parts.add(new FolderSeparator());
                    position++;
                } else {
                    literal.append(c);
                    position++;
                }
            }
            flush(literal, parts);
            return parts;
        }

        private boolean isEscaped(char c) {
            return (c == PLACEHOLDER_START || c == PLACEHOLDER_END || c == OPTIONAL_START || c == OPTIONAL_END)
                    && position + 1 < text.length() && text.charAt(position + 1) == c;
        }

        private Placeholder placeholder() {
            int start = position;
            int end = text.indexOf(PLACEHOLDER_END, start);
            int nextStart = text.indexOf(PLACEHOLDER_START, start + 1);
            if (end < 0 || (nextStart >= 0 && nextStart < end)) {
                throw unmatched(PLACEHOLDER_START, PLACEHOLDER_END);
            }
            String content = text.substring(start + 1, end).strip();
            int separator = content.indexOf(FORMAT_SEPARATOR);
            String name = (separator < 0 ? content : content.substring(0, separator)).strip();
            String formatText = separator < 0 ? null : content.substring(separator + 1).strip();
            if (!placeholders.contains(name)) {
                throw error(LocalizedText.of("scanner.pattern.unknownPlaceholder")
                        .with("placeholder", PLACEHOLDER_START + name + PLACEHOLDER_END));
            }
            NameFormat format = NameFormat.parse(formatText)
                    .orElseThrow(() -> error(LocalizedText.of("scanner.pattern.unknownFormat")
                            .with("format", formatText)
                            .with("placeholder", PLACEHOLDER_START + name + PLACEHOLDER_END)));
            position = end + 1;
            return new Placeholder(name, format);
        }

        private static void flush(StringBuilder literal, List<Part> parts) {
            if (!literal.isEmpty()) {
                parts.add(new Literal(literal.toString()));
                literal.setLength(0);
            }
        }

        private NamePatternException unmatched(char found, char missing) {
            return error(LocalizedText.of("scanner.pattern.unmatched")
                    .with("found", String.valueOf(found))
                    .with("missing", String.valueOf(missing)));
        }

        private NamePatternException error(LocalizedText problem) {
            return new NamePatternException(LocalizedText.of("scanner.pattern.invalid")
                    .with("problem", problem)
                    .with("position", position + 1));
        }
    }
}
