package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Ersetzt nur die Stream-Details ({@code <fileinfo>}) einer vorhandenen NFO. Wie beim
 * {@link PlaybackNfoWriter} wird die Datei als Text bearbeitet, damit alles andere Zeichen für Zeichen
 * erhalten bleibt; nur wenn das nicht sicher geht, wird sie neu geschrieben.
 */
public class StreamDetailsNfoWriter {

    /**
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, StreamDetails details) {
        Element container = existing(nfoFile, NfoElements.MOVIE);
        if (writeAsText(nfoFile, NfoElements.MOVIE, 0, details)) {
            return;
        }
        Element movie = NfoDocuments.children(container, NfoElements.MOVIE).getFirst();
        NfoStreamDetails.write(container.getOwnerDocument(), movie, details);
        NfoFileWriter.write(nfoFile, List.of(movie));
    }

    /**
     * Ändert in einer Mehrfachepisoden-NFO nur die angegebene Episode.
     *
     * @throws NfoException wenn die NFO fehlt, die Episode nicht enthält oder nicht geschrieben werden kann
     */
    public void writeEpisode(Path nfoFile, int season, int episode, StreamDetails details) {
        Element container = existing(nfoFile, NfoElements.EPISODE_DETAILS);
        Element element = EpisodeNfoWriter.existing(container, season, episode)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
        int index = NfoDocuments.children(container, NfoElements.EPISODE_DETAILS).indexOf(element);
        if (writeAsText(nfoFile, NfoElements.EPISODE_DETAILS, index, details)) {
            return;
        }
        NfoStreamDetails.write(container.getOwnerDocument(), element, details);
        NfoFileWriter.write(nfoFile, NfoDocuments.children(container, NfoElements.EPISODE_DETAILS));
    }

    /**
     * Geschrieben wird nur, wenn das Ergebnis gültig ist und genau die Spuren des neuen Blocks enthält.
     *
     * @return {@code false}, wenn die Datei nicht sicher als Text bearbeitet werden kann
     */
    private static boolean writeAsText(Path nfoFile, String rootName, int index, StreamDetails details) {
        if (details.isEmpty()) {
            return true;
        }
        Optional<NfoTextEditor> editor = NfoTextEditor.open(nfoFile);
        Optional<String> indent = editor.flatMap(opened -> opened.childIndent(rootName, index));
        if (indent.isEmpty()) {
            return false;
        }
        String block = NfoStreamDetails.toText(details, indent.get(), editor.get().newline());
        boolean edited = editor.get().setChild(rootName, index, NfoStreamDetails.FILE_INFO, block)
                && carries(editor.get().text(), rootName, index, block);
        if (edited) {
            editor.get().write(nfoFile);
        }
        return edited;
    }

    private static boolean carries(String text, String rootName, int index, String block) {
        try {
            List<Element> roots = NfoFileWriter.parseRoots(text, rootName)
                    .map(container -> NfoDocuments.children(container, rootName)).orElse(List.of());
            Element expected = NfoFileWriter.parseRoots("<" + rootName + ">" + block + "</" + rootName + ">",
                    rootName).orElseThrow();
            return index < roots.size() && Objects.equals(NfoStreamDetails.read(roots.get(index)),
                    NfoStreamDetails.read(NfoDocuments.children(expected, rootName).getFirst()));
        } catch (IOException | SAXException e) {
            return false;
        }
    }

    private static Element existing(Path nfoFile, String rootName) {
        return NfoFileWriter.existingRoots(nfoFile, rootName)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
    }
}
