package de.tiktak.kmm.nfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Replaces child elements of an NFO root element ({@code <movie>}, {@code <tvshow>} …). New elements end up at the
 * position of the previous ones, so the order of an existing NFO is kept.
 */
final class NfoDomEditor {

    private static final String TRUE = "true";

    private NfoDomEditor() {
    }

    /** Replaces the first element of the name or creates it; {@code null} or empty removes all of them. */
    static void setText(Document document, Element parent, String name, String value) {
        List<Element> existing = NfoDocuments.children(parent, name);
        if (value == null || value.isBlank()) {
            existing.forEach(parent::removeChild);
            return;
        }
        Element element = existing.isEmpty() ? appendChild(document, parent, name) : existing.getFirst();
        element.setTextContent(value.strip());
        existing.stream().skip(1).forEach(parent::removeChild);
    }

    /** Replaces all elements of the name with one element per value. */
    static void setTexts(Document document, Element parent, String name, List<String> values) {
        replaceAll(parent, name, values.stream().map(value -> textElement(document, name, value)).toList());
    }

    /** Replaces all elements of the name with the given elements. */
    static void replaceAll(Element parent, String name, List<Element> replacements) {
        List<Element> existing = NfoDocuments.children(parent, name);
        Node position = existing.isEmpty() ? null : existing.getFirst();
        replacements.forEach(element -> parent.insertBefore(element, position));
        existing.forEach(parent::removeChild);
    }

    static Element textElement(Document document, String name, String value) {
        Element element = document.createElement(name);
        element.setTextContent(value.strip());
        return element;
    }

    static Element appendChild(Document document, Element parent, String name) {
        Element element = document.createElement(name);
        parent.appendChild(element);
        return element;
    }

    /** Replaces the IDs; the first one is the default ID. */
    static void setUniqueIds(Document document, Element parent, Map<String, String> uniqueIds) {
        List<Element> elements = new ArrayList<>();
        for (Map.Entry<String, String> id : uniqueIds.entrySet()) {
            Element element = textElement(document, NfoElements.UNIQUE_ID, id.getValue());
            element.setAttribute(NfoElements.TYPE_ATTRIBUTE, id.getKey());
            if (elements.isEmpty()) {
                element.setAttribute(NfoElements.DEFAULT_ATTRIBUTE, TRUE);
            }
            elements.add(element);
        }
        replaceAll(parent, NfoElements.UNIQUE_ID, elements);
    }
}
