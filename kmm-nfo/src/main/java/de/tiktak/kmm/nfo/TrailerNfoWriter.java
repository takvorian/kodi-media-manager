package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Writes only the trailer link ({@code <trailer>}) into an existing movie or TV show NFO. The file is edited as text
 * so everything else is kept character for character; only if that is not safe is it rewritten, in which case only
 * the formatting changes apart from the trailer.
 */
public class TrailerNfoWriter {

    /**
     * @param trailer link, or {@code null} or empty to remove the entry
     * @throws NfoException if the NFO is missing, contains no movie or cannot be written
     */
    public void writeMovie(Path nfoFile, String trailer) {
        writeElement(nfoFile, NfoElements.MOVIE, NfoElements.TRAILER, trailer);
    }

    /**
     * @param trailer link, or {@code null} or empty to remove the entry
     * @throws NfoException if the NFO is missing, contains no TV show or cannot be written
     */
    public void writeTvShow(Path nfoFile, String trailer) {
        writeElement(nfoFile, NfoElements.TV_SHOW, NfoElements.TRAILER, trailer);
    }

    /**
     * Sets or removes a single element directly below the root.
     *
     * @param newValue new text, or {@code null} or empty to remove the element
     */
    static void writeElement(Path nfoFile, String rootName, String elementName, String newValue) {
        Element container = NfoFileWriter.existingRoots(nfoFile, rootName)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
        String value = newValue == null || newValue.isBlank() ? null : newValue.strip();
        Optional<NfoTextEditor> editor = NfoTextEditor.open(nfoFile);
        if (editor.isPresent() && editor.get().setChildText(rootName, 0, elementName, value)
                && carries(editor.get().text(), rootName, elementName, value)) {
            editor.get().write(nfoFile);
            return;
        }
        Element root = NfoDocuments.children(container, rootName).getFirst();
        NfoDomEditor.setText(container.getOwnerDocument(), root, elementName, value);
        NfoFileWriter.write(nfoFile, List.of(root));
    }

    /** Whether the edited text is valid and carries exactly the new value. */
    private static boolean carries(String text, String rootName, String elementName, String value) {
        try {
            List<Element> roots = NfoFileWriter.parseRoots(text, rootName)
                    .map(container -> NfoDocuments.children(container, rootName)).orElse(List.of());
            if (roots.isEmpty()) {
                return false;
            }
            List<Element> elements = NfoDocuments.children(roots.getFirst(), elementName);
            return value == null ? elements.isEmpty()
                    : elements.size() == 1 && value.equals(elements.getFirst().getTextContent());
        } catch (IOException | SAXException e) {
            return false;
        }
    }
}
