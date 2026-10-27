package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Schreibt nur Wiedergabezahl, letzte Wiedergabe und eigene Bewertung in eine vorhandene NFO. Die Bewertung
 * gehört dazu, weil Kodi sie sonst beim Neueinlesen der NFO verliert. Die Datei wird dabei als
 * Text bearbeitet, damit alles andere Zeichen für Zeichen erhalten bleibt (etwa das Format von Ember);
 * nur wenn das nicht sicher geht, wird sie wie beim Speichern der Metadaten neu geschrieben. Eine
 * fehlende NFO legt KMM dafür nicht an, denn Kodi hielte eine fast leere NFO für vollständige Angaben.
 */
public class PlaybackNfoWriter {

    /**
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, NfoPlayback playback) {
        Element container = existing(nfoFile, NfoElements.MOVIE);
        if (writeAsText(nfoFile, NfoElements.MOVIE, 0, playback)) {
            return;
        }
        Element movie = NfoDocuments.children(container, NfoElements.MOVIE).getFirst();
        apply(container.getOwnerDocument(), movie, playback);
        NfoFileWriter.write(nfoFile, List.of(movie));
    }

    /**
     * Ändert in einer Mehrfachepisoden-NFO nur die angegebene Episode.
     *
     * @throws NfoException wenn die NFO fehlt, die Episode nicht enthält oder nicht geschrieben werden kann
     */
    public void writeEpisode(Path nfoFile, int season, int episode, NfoPlayback playback) {
        Element container = existing(nfoFile, NfoElements.EPISODE_DETAILS);
        Element element = EpisodeNfoWriter.existing(container, season, episode)
                .orElseThrow(() -> NfoException.missingEntry(nfoFile));
        int index = NfoDocuments.children(container, NfoElements.EPISODE_DETAILS).indexOf(element);
        if (writeAsText(nfoFile, NfoElements.EPISODE_DETAILS, index, playback)) {
            return;
        }
        apply(container.getOwnerDocument(), element, playback);
        NfoFileWriter.write(nfoFile, NfoDocuments.children(container, NfoElements.EPISODE_DETAILS));
    }

    /**
     * Das Ergebnis wird vor dem Schreiben noch einmal gelesen: Nur wenn es gültig ist und das Element
     * genau die neuen Werte trägt, wird es geschrieben (z. B. nicht bei {@code </movie>} in einem
     * CDATA-Abschnitt).
     *
     * @return {@code false}, wenn die Datei nicht sicher als Text bearbeitet werden kann
     */
    private static boolean writeAsText(Path nfoFile, String rootName, int index, NfoPlayback playback) {
        Expected expected = Expected.of(playback);
        Optional<NfoTextEditor> editor = NfoTextEditor.open(nfoFile);
        boolean edited = editor.isPresent()
                && editor.get().setChildText(rootName, index, NfoElements.PLAY_COUNT, expected.playCount())
                && editor.get().setChildText(rootName, index, NfoElements.LAST_PLAYED, expected.lastPlayed())
                && (expected.userRating() == null
                || editor.get().setChildText(rootName, index, NfoElements.USER_RATING, expected.userRating()))
                && carries(editor.get().text(), rootName, index, expected);
        if (edited) {
            editor.get().write(nfoFile);
        }
        return edited;
    }

    private static boolean carries(String text, String rootName, int index, Expected expected) {
        try {
            List<Element> roots = NfoFileWriter.parseRoots(text, rootName)
                    .map(container -> NfoDocuments.children(container, rootName)).orElse(List.of());
            return index < roots.size()
                    && Objects.equals(childText(roots.get(index), NfoElements.PLAY_COUNT), expected.playCount())
                    && Objects.equals(childText(roots.get(index), NfoElements.LAST_PLAYED), expected.lastPlayed())
                    && (expected.userRating() == null
                    || expected.userRating().equals(childText(roots.get(index), NfoElements.USER_RATING)));
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

    private static void apply(Document document, Element element, NfoPlayback playback) {
        Expected expected = Expected.of(playback);
        NfoDomEditor.setText(document, element, NfoElements.PLAY_COUNT, expected.playCount());
        NfoDomEditor.setText(document, element, NfoElements.LAST_PLAYED, expected.lastPlayed());
        if (expected.userRating() != null) {
            NfoDomEditor.setText(document, element, NfoElements.USER_RATING, expected.userRating());
        }
    }

    /** Die Werte so, wie sie in der NFO stehen sollen. */
    private record Expected(String playCount, String lastPlayed, String userRating) {

        static Expected of(NfoPlayback playback) {
            return new Expected(NfoSupplementWriter.toText(playback.playCount()),
                    NfoSupplementWriter.toText(playback.lastPlayed()),
                    NfoSupplementWriter.toText(playback.userRating()));
        }
    }
}
