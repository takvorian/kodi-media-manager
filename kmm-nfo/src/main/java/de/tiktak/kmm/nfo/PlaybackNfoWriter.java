package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Schreibt nur Wiedergabezahl und letzte Wiedergabe in eine vorhandene NFO. Die Datei wird dabei als
 * Text bearbeitet, damit alles andere Zeichen für Zeichen erhalten bleibt (etwa das Format von Ember);
 * nur wenn das nicht sicher geht, wird sie wie beim Speichern der Metadaten neu geschrieben. Eine
 * fehlende NFO legt KMM dafür nicht an, denn Kodi hielte eine fast leere NFO für vollständige Angaben.
 */
public class PlaybackNfoWriter {

    /**
     * @param playCount  Anzahl der Wiedergaben oder {@code null}, um die Angabe zu entfernen
     * @param lastPlayed letzte Wiedergabe oder {@code null}, um die Angabe zu entfernen
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, Integer playCount, LocalDateTime lastPlayed) {
        Element container = existing(nfoFile, NfoElements.MOVIE);
        if (writeAsText(nfoFile, NfoElements.MOVIE, 0, playCount, lastPlayed)) {
            return;
        }
        Element movie = NfoDocuments.children(container, NfoElements.MOVIE).getFirst();
        apply(container.getOwnerDocument(), movie, playCount, lastPlayed);
        NfoFileWriter.write(nfoFile, List.of(movie));
    }

    /**
     * Ändert in einer Mehrfachepisoden-NFO nur die angegebene Episode.
     *
     * @throws NfoException wenn die NFO fehlt, die Episode nicht enthält oder nicht geschrieben werden kann
     */
    public void writeEpisode(Path nfoFile, int season, int episode, Integer playCount, LocalDateTime lastPlayed) {
        Element container = existing(nfoFile, NfoElements.EPISODE_DETAILS);
        Element element = EpisodeNfoWriter.existing(container, season, episode)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
        int index = NfoDocuments.children(container, NfoElements.EPISODE_DETAILS).indexOf(element);
        if (writeAsText(nfoFile, NfoElements.EPISODE_DETAILS, index, playCount, lastPlayed)) {
            return;
        }
        apply(container.getOwnerDocument(), element, playCount, lastPlayed);
        NfoFileWriter.write(nfoFile, NfoDocuments.children(container, NfoElements.EPISODE_DETAILS));
    }

    /**
     * Das Ergebnis wird vor dem Schreiben noch einmal gelesen: Nur wenn es gültig ist und das Element
     * genau die neuen Werte trägt, wird es geschrieben (z. B. nicht bei {@code </movie>} in einem
     * CDATA-Abschnitt).
     *
     * @return {@code false}, wenn die Datei nicht sicher als Text bearbeitet werden kann
     */
    private static boolean writeAsText(Path nfoFile, String rootName, int index, Integer playCount,
                                       LocalDateTime lastPlayed) {
        String count = NfoSupplementWriter.toText(playCount);
        String last = NfoSupplementWriter.toText(lastPlayed);
        Optional<NfoTextEditor> editor = NfoTextEditor.open(nfoFile);
        boolean edited = editor.isPresent()
                && editor.get().setChildText(rootName, index, NfoElements.PLAY_COUNT, count)
                && editor.get().setChildText(rootName, index, NfoElements.LAST_PLAYED, last)
                && carries(editor.get().text(), rootName, index, count, last);
        if (edited) {
            editor.get().write(nfoFile);
        }
        return edited;
    }

    private static boolean carries(String text, String rootName, int index, String playCount, String lastPlayed) {
        try {
            List<Element> roots = NfoFileWriter.parseRoots(text, rootName)
                    .map(container -> NfoDocuments.children(container, rootName)).orElse(List.of());
            return index < roots.size()
                    && Objects.equals(childText(roots.get(index), NfoElements.PLAY_COUNT), playCount)
                    && Objects.equals(childText(roots.get(index), NfoElements.LAST_PLAYED), lastPlayed);
        } catch (IOException | SAXException e) {
            return false;
        }
    }

    private static String childText(Element element, String name) {
        List<Element> children = NfoDocuments.children(element, name);
        return children.size() == 1 ? children.getFirst().getTextContent() : null;
    }

    private static Element existing(Path nfoFile, String rootName) {
        return NfoFileWriter.existingRoots(nfoFile, rootName)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
    }

    private static void apply(Document document, Element element, Integer playCount, LocalDateTime lastPlayed) {
        NfoDomEditor.setText(document, element, NfoElements.PLAY_COUNT, NfoSupplementWriter.toText(playCount));
        NfoDomEditor.setText(document, element, NfoElements.LAST_PLAYED, NfoSupplementWriter.toText(lastPlayed));
    }
}
