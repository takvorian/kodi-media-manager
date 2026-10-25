package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Gemeinsame DOM-Hilfen für Lesen und Schreiben von NFOs.
 */
final class NfoDocuments {

    private static final String MOVIE_START = "<" + NfoElements.MOVIE;
    private static final String MOVIE_END = "</" + NfoElements.MOVIE + ">";

    private NfoDocuments() {
    }

    /**
     * Schneidet das {@code <movie>}-Element aus dem Dateiinhalt. Kodi erlaubt davor eine
     * XML-Deklaration und danach eine URL, die beim Parsen sonst stören würden.
     */
    static Optional<String> movieXml(String content) {
        int start = content.indexOf(MOVIE_START);
        int end = content.lastIndexOf(MOVIE_END);
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        return Optional.of(content.substring(start, end + MOVIE_END.length()));
    }

    /** Dateiinhalt ohne das {@code <movie>}-Element, z. B. eine angehängte URL. */
    static String outsideMovieXml(String content) {
        return movieXml(content).map(xml -> content.replace(xml, " ")).orElse(content);
    }

    static Document parse(String xml) throws IOException, SAXException {
        return SecureXml.documentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Getrimmter Text des ersten Kind-Elements oder {@code null}, wenn es fehlt oder leer ist. */
    static String text(Element parent, String name) {
        List<Element> elements = children(parent, name);
        if (elements.isEmpty()) {
            return null;
        }
        String value = elements.getFirst().getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    /** Getrimmte, nicht leere Texte aller Kind-Elemente des Namens. */
    static List<String> texts(Element parent, String name) {
        return children(parent, name).stream()
                .map(element -> element.getTextContent().strip())
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /** Direkte Kind-Elemente mit dem Namen, ohne Beachtung der Groß-/Kleinschreibung. */
    static List<Element> children(Element parent, String name) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equalsIgnoreCase(name)) {
                elements.add((Element) node);
            }
        }
        return elements;
    }
}
