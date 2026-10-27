package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
 * Liest vorhandene NFOs zum Bearbeiten und schreibt sie zurück. Die Datei wird über eine temporäre
 * Datei ersetzt, damit bei Fehlern keine halbe NFO entsteht.
 */
@Slf4j
final class NfoFileWriter {

    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";
    private static final String INDENT_SPACES = "4";
    private static final String YES = "yes";
    private static final String TEMP_SUFFIX = ".kmm-tmp";

    private NfoFileWriter() {
    }

    /**
     * Die Wurzelelemente einer vorhandenen NFO, ohne Einrückungen. Mehrfachepisoden enthalten mehrere
     * Wurzelelemente hintereinander; sie werden unter einem künstlichen Element zusammengefasst.
     *
     * @return leer, wenn die Datei fehlt, kein solches Element enthält oder kein gültiges XML ist
     */
    static Optional<Element> existingRoots(Path nfoFile, String rootName) {
        if (!Files.isRegularFile(nfoFile)) {
            return Optional.empty();
        }
        try {
            return parseRoots(Files.readString(nfoFile, StandardCharsets.UTF_8), rootName);
        } catch (IOException | SAXException e) {
            // Eine kaputte NFO wird ersetzt statt den Speichervorgang scheitern zu lassen.
            log.warn("Existing NFO {} is not valid XML and will be replaced: {}", nfoFile, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Die Wurzelelemente eines NFO-Texts wie bei {@link #existingRoots(Path, String)}.
     *
     * @return leer, wenn der Text kein solches Element enthält
     * @throws SAXException wenn der Text kein gültiges XML ist
     */
    static Optional<Element> parseRoots(String content, String rootName) throws IOException, SAXException {
        Optional<String> xml = NfoDocuments.rootXml(content, rootName);
        if (xml.isEmpty()) {
            return Optional.empty();
        }
        Element container = NfoDocuments.parseAll(xml.get()).getDocumentElement();
        removeWhitespaceText(container);
        return Optional.of(container);
    }

    /** Leerer Container für eine neue NFO; die Wurzelelemente hängt der Aufrufer an. */
    static Element newContainer() {
        Document document = SecureXml.documentBuilder().newDocument();
        Element container = document.createElement(NfoDocuments.CONTAINER);
        document.appendChild(container);
        return container;
    }

    /** Schreibt die Elemente nacheinander, mit einer gemeinsamen XML-Deklaration. */
    static void write(Path nfoFile, List<Element> roots) {
        try {
            StringBuilder content = new StringBuilder(XML_DECLARATION).append(System.lineSeparator());
            for (Element root : roots) {
                content.append(serialize(root).strip()).append(System.lineSeparator());
            }
            writeBytes(nfoFile, content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (TransformerException e) {
            throw NfoException.unwritable(nfoFile, e);
        }
    }

    /** Ersetzt den Inhalt der Datei über eine temporäre Datei. */
    static void writeBytes(Path nfoFile, byte[] content) {
        Path temporary = nfoFile.resolveSibling(nfoFile.getFileName() + TEMP_SUFFIX);
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                output.write(content);
            }
            replace(temporary, nfoFile);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw NfoException.unwritable(nfoFile, e);
        }
    }

    private static String serialize(Element element) throws TransformerException {
        StringWriter writer = new StringWriter();
        transformer().transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
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
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, YES);
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
}
