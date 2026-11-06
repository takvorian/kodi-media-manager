package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes simple elements directly in the text of an NFO. Indentation, line endings, header, comments and the order
 * of attributes are kept character for character; in a line diff, changed files only differ in the affected lines.
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
     * @return empty if the file cannot be read as UTF-8, like all NFOs in KMM
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
     * Sets the text of a direct child element of the {@code index}-th root element {@code rootName}; an existing
     * element is replaced, a missing one is inserted before the closing tag.
     *
     * @param value new text, or {@code null} to remove the element
     * @return {@code false} if the root element cannot be found unambiguously in the text
     */
    boolean setChildText(String rootName, int index, String childName, String value) {
        return setChild(rootName, index, childName, value == null ? null : tag(childName, value));
    }

    /**
     * Like {@link #setChildText}, but with a complete element including its children.
     *
     * @param element the element as text, without indentation before the first line and line ending after the last
     *                line, or {@code null} to remove the element
     */
    boolean setChild(String rootName, int index, String childName, String element) {
        Optional<Span> root = rootElement(rootName, index);
        root.ifPresent(span -> text = text.substring(0, span.start())
                + withChild(text.substring(span.start(), span.end()), rootName, childName, element)
                + text.substring(span.end()));
        return root.isPresent();
    }

    /** @return the indentation of the child elements of the root element, two spaces if in doubt */
    Optional<String> childIndent(String rootName, int index) {
        return rootElement(rootName, index).map(span -> indentOf(text.substring(span.start(), span.end())));
    }

    String newline() {
        return newline;
    }

    void write(Path nfoFile) {
        NfoFileWriter.writeBytes(nfoFile, text.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the element including its closing tag */
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

    private String withChild(String element, String rootName, String childName, String rendered) {
        Matcher existing = Pattern.compile("([ \\t]*)<" + childName + "(?:\\s[^>]*)?(?:/>|>.*?</" + childName
                + "\\s*>)(\\r?\\n)?", Pattern.DOTALL).matcher(element);
        StringBuilder result = new StringBuilder();
        boolean replaced = false;
        while (existing.find()) {
            String replacement = "";
            if (rendered != null && !replaced) {
                String lineEnd = existing.group(2) == null ? "" : existing.group(2);
                replacement = existing.group(1) + rendered + lineEnd;
                replaced = true;
            }
            existing.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        existing.appendTail(result);
        return rendered == null || replaced ? result.toString() : inserted(result.toString(), rootName, rendered);
    }

    private String inserted(String element, String rootName, String rendered) {
        int closing = element.lastIndexOf("</" + rootName);
        int lineStart = element.lastIndexOf('\n', closing) + 1;
        String indent = indentOf(element);
        if (element.substring(lineStart, closing).isBlank()) {
            return element.substring(0, lineStart) + indent + rendered + newline
                    + element.substring(lineStart);
        }
        return element.substring(0, closing) + newline + indent + rendered + newline
                + element.substring(closing);
    }

    private static String indentOf(String element) {
        Matcher indent = FIRST_CHILD_INDENT.matcher(element);
        return indent.find() && !indent.group(1).isEmpty() ? indent.group(1) : DEFAULT_INDENT;
    }

    static String tag(String name, String value) {
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<%s>%s</%s>".formatted(name, escaped, name);
    }

    /** Section of the text; {@code end} is exclusive. */
    private record Span(int start, int end) {
    }
}
