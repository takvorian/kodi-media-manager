package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ändert einfache Elemente direkt im Text einer NFO. Einrückung, Zeilenenden, Kopfzeile, Kommentare und
 * die Reihenfolge der Attribute bleiben Zeichen für Zeichen erhalten; geänderte Dateien unterscheiden
 * sich im Zeilenvergleich nur in den betroffenen Zeilen.
 */
final class NfoTextEditor {

    private static final Pattern FIRST_CHILD_INDENT = Pattern.compile("\\r?\\n([ \\t]*)<");
    private static final String DEFAULT_INDENT = "  ";
    private static final String CRLF = "\r\n";
    private static final String LF = "\n";

    private final String newline;
    private String text;

    private NfoTextEditor(String text) {
        this.text = text;
        this.newline = text.contains(CRLF) ? CRLF : LF;
    }

    /**
     * @return leer, wenn die Datei nicht als UTF-8 gelesen werden kann; wie alle NFOs in KMM
     */
    static Optional<NfoTextEditor> open(Path nfoFile) {
        try {
            return Optional.of(new NfoTextEditor(Files.readString(nfoFile, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    String text() {
        return text;
    }

    /**
     * Setzt den Text eines direkten Kindelements des {@code index}-ten Wurzelelements {@code rootName};
     * ein vorhandenes Element wird ersetzt, ein fehlendes vor dem schließenden Tag eingefügt.
     *
     * @param value neuer Text oder {@code null}, um das Element zu entfernen
     * @return {@code false}, wenn das Wurzelelement im Text nicht eindeutig zu finden ist
     */
    boolean setChildText(String rootName, int index, String childName, String value) {
        Optional<Span> root = rootElement(rootName, index);
        root.ifPresent(span -> text = text.substring(0, span.start())
                + withChild(text.substring(span.start(), span.end()), rootName, childName, value)
                + text.substring(span.end()));
        return root.isPresent();
    }

    void write(Path nfoFile) {
        NfoFileWriter.writeBytes(nfoFile, text.getBytes(StandardCharsets.UTF_8));
    }

    /** @return das Element einschließlich seines schließenden Tags */
    private Optional<Span> rootElement(String rootName, int index) {
        Matcher open = Pattern.compile("<" + rootName + "(?:\\s[^>]*)?(?<!/)>").matcher(text);
        Pattern closing = Pattern.compile("</" + rootName + "\\s*>");
        int searchFrom = 0;
        for (int current = 0; open.find(searchFrom); current++) {
            Matcher close = closing.matcher(text);
            if (!close.find(open.end())) {
                return Optional.empty();
            }
            if (current == index) {
                return Optional.of(new Span(open.start(), close.end()));
            }
            searchFrom = close.end();
        }
        return Optional.empty();
    }

    private String withChild(String element, String rootName, String childName, String value) {
        Matcher existing = Pattern.compile("([ \\t]*)<" + childName + "(?:\\s[^>]*)?(?:/>|>.*?</" + childName
                + "\\s*>)(\\r?\\n)?", Pattern.DOTALL).matcher(element);
        StringBuilder result = new StringBuilder();
        boolean replaced = false;
        while (existing.find()) {
            String replacement = "";
            if (value != null && !replaced) {
                String lineEnd = existing.group(2) == null ? "" : existing.group(2);
                replacement = existing.group(1) + tag(childName, value) + lineEnd;
                replaced = true;
            }
            existing.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        existing.appendTail(result);
        return value == null || replaced ? result.toString() : inserted(result.toString(), rootName, childName, value);
    }

    private String inserted(String element, String rootName, String childName, String value) {
        int closing = element.lastIndexOf("</" + rootName);
        int lineStart = element.lastIndexOf('\n', closing) + 1;
        String indent = indentOf(element);
        if (element.substring(lineStart, closing).isBlank()) {
            return element.substring(0, lineStart) + indent + tag(childName, value) + newline
                    + element.substring(lineStart);
        }
        return element.substring(0, closing) + newline + indent + tag(childName, value) + newline
                + element.substring(closing);
    }

    private static String indentOf(String element) {
        Matcher indent = FIRST_CHILD_INDENT.matcher(element);
        return indent.find() && !indent.group(1).isEmpty() ? indent.group(1) : DEFAULT_INDENT;
    }

    private static String tag(String name, String value) {
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<%s>%s</%s>".formatted(name, escaped, name);
    }

    /** Abschnitt im Text; {@code end} ist exklusiv. */
    private record Span(int start, int end) {
    }
}
