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
 * Shared DOM helpers for reading and writing NFOs.
 */
final class NfoDocuments {

    /** Artificial element under which several root elements of an NFO are parsed. */
    static final String CONTAINER = "kmm-nfo";

    private NfoDocuments() {
    }

    /**
     * Cuts the {@code <movie>} element out of the file content. Kodi allows an XML declaration before it and a URL
     * after it, which would otherwise disturb parsing.
     */
    static Optional<String> movieXml(String content) {
        return rootXml(content, NfoElements.MOVIE);
    }

    /**
     * Cuts all elements of the name out of the file content, from the first start to the last end. Multi-episode
     * files contain several {@code <episodedetails>} in a row.
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

    /** File content without the {@code <movie>} element, e.g. an appended URL. */
    static String outsideMovieXml(String content) {
        return movieXml(content).map(xml -> content.replace(xml, " ")).orElse(content);
    }

    static Document parse(String xml) throws IOException, SAXException {
        return SecureXml.documentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Parses one or more consecutive elements under the {@link #CONTAINER}. */
    static Document parseAll(String xml) throws IOException, SAXException {
        return parse("<" + CONTAINER + ">" + xml + "</" + CONTAINER + ">");
    }

    /** Trimmed text of the first child element, or {@code null} if it is missing or empty. */
    static String text(Element parent, String name) {
        List<Element> elements = children(parent, name);
        if (elements.isEmpty()) {
            return null;
        }
        String value = elements.getFirst().getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    /** Trimmed, non-empty texts of all child elements of the name. */
    static List<String> texts(Element parent, String name) {
        return children(parent, name).stream()
                .map(element -> element.getTextContent().strip())
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /** Direct child elements with the name, ignoring case. */
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
