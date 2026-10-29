package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Schreibt nur den Trailer-Link ({@code <trailer>}) in eine vorhandene Film- oder Serien-NFO. Die Datei wird dabei als
 * Text bearbeitet, damit alles andere Zeichen für Zeichen erhalten bleibt; nur wenn das nicht sicher geht, wird sie
 * neu geschrieben, wobei sich außer dem Trailer nur die Formatierung ändert.
 */
public class TrailerNfoWriter {

    /**
     * @param trailer Link oder {@code null} bzw. leer, um den Eintrag zu entfernen
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, String trailer) {
        write(nfoFile, NfoElements.MOVIE, trailer);
    }

    /**
     * @param trailer Link oder {@code null} bzw. leer, um den Eintrag zu entfernen
     * @throws NfoException wenn die NFO fehlt, keine Serie enthält oder nicht geschrieben werden kann
     */
    public void writeTvShow(Path nfoFile, String trailer) {
        write(nfoFile, NfoElements.TV_SHOW, trailer);
    }

    private static void write(Path nfoFile, String rootName, String trailer) {
        Element container = NfoFileWriter.existingRoots(nfoFile, rootName)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
        String value = trailer == null || trailer.isBlank() ? null : trailer.strip();
        Optional<NfoTextEditor> editor = NfoTextEditor.open(nfoFile);
        if (editor.isPresent() && editor.get().setChildText(rootName, 0, NfoElements.TRAILER, value)
                && carries(editor.get().text(), rootName, value)) {
            editor.get().write(nfoFile);
            return;
        }
        Element root = NfoDocuments.children(container, rootName).getFirst();
        NfoDomEditor.setText(container.getOwnerDocument(), root, NfoElements.TRAILER, value);
        NfoFileWriter.write(nfoFile, List.of(root));
    }

    /** Ob der bearbeitete Text gültig ist und genau den neuen Trailer trägt. */
    private static boolean carries(String text, String rootName, String value) {
        try {
            List<Element> roots = NfoFileWriter.parseRoots(text, rootName)
                    .map(container -> NfoDocuments.children(container, rootName)).orElse(List.of());
            if (roots.isEmpty()) {
                return false;
            }
            List<Element> trailers = NfoDocuments.children(roots.getFirst(), NfoElements.TRAILER);
            return value == null ? trailers.isEmpty()
                    : trailers.size() == 1 && value.equals(trailers.getFirst().getTextContent());
        } catch (IOException | SAXException e) {
            return false;
        }
    }
}
