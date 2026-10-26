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

    /** Künstliches Element, unter dem mehrere Wurzelelemente einer NFO geparst werden. */
    static final String CONTAINER = "kmm-nfo";

    private NfoDocuments() {
    }

    /**
     * Schneidet das {@code <movie>}-Element aus dem Dateiinhalt. Kodi erlaubt davor eine
     * XML-Deklaration und danach eine URL, die beim Parsen sonst stören würden.
     */
    static Optional<String> movieXml(String content) {
        return rootXml(content, NfoElements.MOVIE);
    }

    /**
     * Schneidet alle Elemente des Namens aus dem Dateiinhalt, vom ersten Anfang bis zum letzten Ende.
     * Mehrfachepisoden enthalten mehrere {@code <episodedetails>} hintereinander.
     */
    static Optional<String> rootXml(String content, String rootName) {
        String startTag = "<" + rootName;
        String endTag = "</" + rootName + ">";
        int start = content.indexOf(startTag);
        int end = content.lastIndexOf(endTag);
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        return Optional.of(content.substring(start, end + endTag.length()));
    }

    /** Dateiinhalt ohne das {@code <movie>}-Element, z. B. eine angehängte URL. */
    static String outsideMovieXml(String content) {
        return movieXml(content).map(xml -> content.replace(xml, " ")).orElse(content);
    }

    static Document parse(String xml) throws IOException, SAXException {
        return SecureXml.documentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Parst ein oder mehrere aufeinanderfolgende Elemente unter dem {@link #CONTAINER}. */
    static Document parseAll(String xml) throws IOException, SAXException {
        return parse("<" + CONTAINER + ">" + xml + "</" + CONTAINER + ">");
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
