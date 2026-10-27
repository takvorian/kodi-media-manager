package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Ersetzt nur die Stream-Details ({@code <fileinfo>}) einer vorhandenen NFO. Wie beim
 * {@link PlaybackNfoWriter} wird die Datei als Text bearbeitet, damit alles andere Zeichen für Zeichen
 * erhalten bleibt; nur wenn das nicht sicher geht, wird sie neu geschrieben. Jede Datei wird genau einmal
 * ersetzt: Mehrmaliges Ersetzen kurz hintereinander scheitert auf Netzlaufwerken gelegentlich.
 */
public class StreamDetailsNfoWriter {

    /**
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, StreamDetails details) {
        Element container = existing(nfoFile, NfoElements.MOVIE);
        if (details.isEmpty() || writeAsText(nfoFile, NfoElements.MOVIE, Map.of(0, details))) {
            return;
        }
        Element movie = NfoDocuments.children(container, NfoElements.MOVIE).getFirst();
        NfoStreamDetails.write(container.getOwnerDocument(), movie, details);
        NfoFileWriter.write(nfoFile, List.of(movie));
    }

    /**
     * Ändert in einer Mehrfachepisoden-NFO nur die angegebenen Episoden; Episoden ohne Stream-Details
     * bleiben unverändert.
     *
     * @throws NfoException wenn die NFO fehlt, eine der Episoden nicht enthält oder nicht geschrieben werden kann
     */
    public void writeEpisodes(Path nfoFile, List<EpisodeStreamDetails> episodes) {
        Element container = existing(nfoFile, NfoElements.EPISODE_DETAILS);
        List<Element> elements = NfoDocuments.children(container, NfoElements.EPISODE_DETAILS);
        Map<Integer, StreamDetails> byIndex = new LinkedHashMap<>();
        for (EpisodeStreamDetails episode : episodes) {
            Element element = EpisodeNfoWriter.existing(container, episode.season(), episode.episode())
                    .orElseThrow(() -> NfoException.missingEntry(nfoFile));
            if (!episode.details().isEmpty()) {
                byIndex.put(elements.indexOf(element), episode.details());
            }
        }
        if (byIndex.isEmpty() || writeAsText(nfoFile, NfoElements.EPISODE_DETAILS, byIndex)) {
            return;
        }
        byIndex.forEach((index, details) ->
                NfoStreamDetails.write(container.getOwnerDocument(), elements.get(index), details));
        NfoFileWriter.write(nfoFile, elements);
    }

    /**
     * Geschrieben wird nur, wenn das Ergebnis gültig ist und genau die Spuren der neuen Blöcke enthält.
     *
     * @param byIndex die neuen Stream-Details je Position des Wurzelelements
     * @return {@code false}, wenn die Datei nicht sicher als Text bearbeitet werden kann
     */
    private static boolean writeAsText(Path nfoFile, String rootName, Map<Integer, StreamDetails> byIndex) {
        Optional<NfoTextEditor> opened = NfoTextEditor.open(nfoFile);
        if (opened.isEmpty()) {
            return false;
        }
        NfoTextEditor editor = opened.get();
        Map<Integer, String> blocks = new LinkedHashMap<>();
        for (Map.Entry<Integer, StreamDetails> entry : byIndex.entrySet()) {
            int index = entry.getKey();
            Optional<String> indent = editor.childIndent(rootName, index);
            if (indent.isEmpty()) {
                return false;
            }
            String block = NfoStreamDetails.toText(entry.getValue(), indent.get(), editor.newline());
            if (!editor.setChild(rootName, index, NfoStreamDetails.FILE_INFO, block)) {
                return false;
            }
            blocks.put(index, block);
        }
        boolean edited = blocks.entrySet().stream()
                .allMatch(block -> carries(editor.text(), rootName, block.getKey(), block.getValue()));
        if (edited) {
            editor.write(nfoFile);
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
