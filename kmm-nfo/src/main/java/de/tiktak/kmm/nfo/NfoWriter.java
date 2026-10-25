package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

/**
 * Schreibt Kodi-Film-NFOs. Ist bereits eine NFO vorhanden, werden nur die von KMM verwalteten
 * Elemente ersetzt; alles andere (Stream-Details, Trailer, Bild-URLs …) bleibt erhalten. Ohne bekannte
 * weitere Angaben ({@link MovieNfo#supplement()}) bleiben auch Darsteller, Bewertungen usw. unverändert.
 * Die Datei wird über eine temporäre Datei ersetzt, damit bei Fehlern keine halbe NFO entsteht.
 */
@Slf4j
public class NfoWriter {

    private static final String INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";
    private static final String INDENT_SPACES = "4";
    private static final String STANDALONE = "yes";
    private static final String YES = "yes";
    private static final String TRUE = "true";
    private static final String TEMP_SUFFIX = ".kmm-tmp";

    public void write(Path nfoFile, MovieNfo nfo) {
        Document document = existingDocument(nfoFile).orElseGet(NfoWriter::newDocument);
        Element movie = document.getDocumentElement();
        removeWhitespaceText(movie);
        apply(document, movie, nfo);
        save(nfoFile, document);
    }

    private static void apply(Document document, Element movie, MovieNfo nfo) {
        NfoDomEditor.setText(document, movie, NfoElements.TITLE, nfo.title());
        NfoDomEditor.setText(document, movie, NfoElements.ORIGINAL_TITLE, nfo.originalTitle());
        NfoDomEditor.setText(document, movie, NfoElements.SORT_TITLE, nfo.sortTitle());
        NfoDomEditor.setText(document, movie, NfoElements.YEAR, toText(nfo.effectiveYear()));
        NfoDomEditor.setText(document, movie, NfoElements.PREMIERED, toText(nfo.premiered()));
        NfoDomEditor.setText(document, movie, NfoElements.PLOT, nfo.plot());
        NfoDomEditor.setText(document, movie, NfoElements.OUTLINE, nfo.outline());
        NfoDomEditor.setText(document, movie, NfoElements.TAGLINE, nfo.tagline());
        NfoDomEditor.setText(document, movie, NfoElements.RUNTIME, toText(nfo.runtime()));
        NfoDomEditor.setText(document, movie, NfoElements.MPAA, nfo.mpaa());
        NfoDomEditor.setTexts(document, movie, NfoElements.GENRE, nfo.genres());
        setUniqueIds(document, movie, nfo.uniqueIds());
        if (nfo.supplement() != null) {
            NfoSupplementWriter.write(document, movie, nfo.supplement());
        }
    }

    private static Optional<Document> existingDocument(Path nfoFile) {
        if (!Files.isRegularFile(nfoFile)) {
            return Optional.empty();
        }
        try {
            Optional<String> xml = NfoDocuments.movieXml(Files.readString(nfoFile, StandardCharsets.UTF_8));
            return xml.isEmpty() ? Optional.empty() : Optional.of(NfoDocuments.parse(xml.get()));
        } catch (IOException | SAXException e) {
            // Eine kaputte NFO wird ersetzt statt den Speichervorgang scheitern zu lassen.
            log.warn("Existing NFO {} is not valid XML and will be replaced: {}", nfoFile, e.getMessage());
            return Optional.empty();
        }
    }

    private static Document newDocument() {
        Document document = SecureXml.documentBuilder().newDocument();
        document.appendChild(document.createElement(NfoElements.MOVIE));
        return document;
    }

    private static void setUniqueIds(Document document, Element movie, Map<String, String> uniqueIds) {
        List<Element> elements = new ArrayList<>();
        for (Map.Entry<String, String> id : uniqueIds.entrySet()) {
            Element element = NfoDomEditor.textElement(document, NfoElements.UNIQUE_ID, id.getValue());
            element.setAttribute(NfoElements.TYPE_ATTRIBUTE, id.getKey());
            if (elements.isEmpty()) {
                element.setAttribute(NfoElements.DEFAULT_ATTRIBUTE, TRUE);
            }
            elements.add(element);
        }
        NfoDomEditor.replaceAll(movie, NfoElements.UNIQUE_ID, elements);
    }

    /** Entfernt Einrückungen der alten Datei, damit der Transformer sauber neu einrücken kann. */
    private static void removeWhitespaceText(Node node) {
        NodeList children = node.getChildNodes();
        for (int index = children.getLength() - 1; index >= 0; index--) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceText(child);
            }
        }
    }

    private static void save(Path nfoFile, Document document) {
        Path temporary = nfoFile.resolveSibling(nfoFile.getFileName() + TEMP_SUFFIX);
        try {
            document.setXmlStandalone(true);
            try (OutputStream output = Files.newOutputStream(temporary)) {
                transformer().transform(new DOMSource(document), new StreamResult(output));
            }
            replace(temporary, nfoFile);
        } catch (IOException | TransformerException e) {
            deleteQuietly(temporary);
            throw NfoException.unwritable(nfoFile, e);
        }
    }

    /** Netzlaufwerke unterstützen teils kein atomares Ersetzen; dann wird normal verschoben. */
    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Transformer transformer() {
        Transformer transformer = SecureXml.transformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.STANDALONE, STANDALONE);
        transformer.setOutputProperty(OutputKeys.INDENT, YES);
        transformer.setOutputProperty(INDENT_AMOUNT, INDENT_SPACES);
        return transformer;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Temporary file {} could not be deleted: {}", file, e.getMessage());
        }
    }

    private static String toText(Object value) {
        return value == null ? null : value.toString();
    }
}
