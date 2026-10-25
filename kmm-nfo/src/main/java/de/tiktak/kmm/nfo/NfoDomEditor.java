package de.tiktak.kmm.nfo;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Ersetzt Kind-Elemente des {@code <movie>}-Elements. Neue Elemente landen an der Position der
 * bisherigen, damit die Reihenfolge einer vorhandenen NFO erhalten bleibt.
 */
final class NfoDomEditor {

    private NfoDomEditor() {
    }

    /** Ersetzt das erste Element des Namens oder legt es an; {@code null} oder leer entfernt alle. */
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

    /** Ersetzt alle Elemente des Namens durch je ein Element pro Wert. */
    static void setTexts(Document document, Element parent, String name, List<String> values) {
        replaceAll(parent, name, values.stream().map(value -> textElement(document, name, value)).toList());
    }

    /** Ersetzt alle Elemente des Namens durch die übergebenen Elemente. */
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
}
