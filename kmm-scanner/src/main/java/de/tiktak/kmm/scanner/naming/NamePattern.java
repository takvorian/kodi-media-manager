package de.tiktak.kmm.scanner.naming;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Muster für Ordner- und Dateinamen, etwa {@code {title}[ ({year})]}.
 * <ul>
 * <li>{@code {name}} ist ein Platzhalter, {@code {name:format}} ein formatierter (siehe {@link NameFormat}).</li>
 * <li>{@code [ … ]} ist ein optionaler Teil: Er entfällt, wenn ein Platzhalter darin leer ist.</li>
 * <li>{@code /} und {@code \} trennen Ordner, etwa {@code {title:first}/{title}}.</li>
 * <li>{@code {{}, {@code }}}, {@code [[} und {@code ]]} stehen für die Zeichen selbst.</li>
 * </ul>
 * Die Werte werden vor dem Einsetzen so bereinigt, dass sie keine Ordner trennen; das Ergebnis wird je
 * Ordner bzw. Datei mit {@link FileNames#clean} gültig gemacht.
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
     * @param placeholders die erlaubten Platzhalter
     * @throws NamePatternException wenn das Muster ungültig ist; die Meldung eignet sich für die Oberfläche
     */
    public static NamePattern parse(String text, Set<String> placeholders) {
        Objects.requireNonNull(placeholders, "placeholders must not be null");
        if (text == null || text.isBlank()) {
            throw new NamePatternException("Das Muster ist leer.");
        }
        return new NamePattern(text, new Parser(text, placeholders).parse());
    }

    public String text() {
        return text;
    }

    /** Ob das Muster Ordner trennt, also mehrere Namensteile ergibt. */
    public boolean hasFolders() {
        return parts.stream().anyMatch(Part::separatesFolders);
    }

    /**
     * @param values liefert den Wert eines Platzhalters; {@code null} oder leer heißt „nicht gesetzt“
     * @return die bereinigten Namensteile, je Ordner einer; leere Teile entfallen
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

    /** Ein Teil des Musters: Text, Platzhalter, optionaler Teil oder Ordnertrenner. */
    private sealed interface Part {

        /** @return der Text oder leer, wenn ein optionaler Teil entfällt */
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

    /** Ordnertrenner; im Zwischenergebnis ein Nullzeichen, das in keinem Wert vorkommen kann. */
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

    /** Zerlegt den Mustertext; Fehler nennen die Stelle (ab 1). */
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
                throw error("„]“ ohne passendes „[“");
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
                    throw error("„}“ ohne passendes „{“");
                } else if (c == OPTIONAL_START) {
                    if (inOptional) {
                        throw error("Optionale Teile lassen sich nicht verschachteln");
                    }
                    flush(literal, parts);
                    int start = position++;
                    List<Part> optional = parseParts(true);
                    if (position >= text.length()) {
                        position = start;
                        throw error("„[“ ohne passendes „]“");
                    }
                    position++;
                    parts.add(new OptionalPart(List.copyOf(optional)));
                } else if (c == OPTIONAL_END) {
                    if (!inOptional) {
                        throw error("„]“ ohne passendes „[“");
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
                throw error("„{“ ohne passendes „}“");
            }
            String content = text.substring(start + 1, end).strip();
            int separator = content.indexOf(FORMAT_SEPARATOR);
            String name = (separator < 0 ? content : content.substring(0, separator)).strip();
            String formatText = separator < 0 ? null : content.substring(separator + 1).strip();
            if (!placeholders.contains(name)) {
                throw error("Unbekannter Platzhalter „{" + name + "}“");
            }
            NameFormat format = NameFormat.parse(formatText)
                    .orElseThrow(() -> error("Unbekanntes Format „" + formatText + "“ bei „{" + name + "}“"));
            position = end + 1;
            return new Placeholder(name, format);
        }

        private static void flush(StringBuilder literal, List<Part> parts) {
            if (!literal.isEmpty()) {
                parts.add(new Literal(literal.toString()));
                literal.setLength(0);
            }
        }

        private NamePatternException error(String message) {
            return new NamePatternException(message + " (Stelle " + (position + 1) + ").");
        }
    }
}
