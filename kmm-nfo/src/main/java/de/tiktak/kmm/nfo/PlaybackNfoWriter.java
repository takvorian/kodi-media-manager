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
 * Writes only the play count, last playback and own rating into an existing NFO. The rating is part of it because
 * Kodi would otherwise lose it when it reads the NFO again. The file is edited as text so everything else is kept
 * character for character (such as Ember's format); only if that is not safe is it rewritten as when saving the
 * metadata. KMM does not create a missing NFO for this, because Kodi would take an almost empty NFO for complete
 * details.
 */
public class PlaybackNfoWriter {

    /**
     * @throws NfoException if the NFO is missing, contains no movie or cannot be written
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
     * Changes only the given episode in a multi-episode NFO.
     *
     * @throws NfoException if the NFO is missing, does not contain the episode or cannot be written
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
     * The result is read again before writing: it is only written if it is valid and the element carries exactly
     * the new values (e.g. not for {@code </movie>} in a CDATA section).
     *
     * @return {@code false} if the file cannot safely be edited as text
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

    /** The values as they should appear in the NFO. */
    private record Expected(String playCount, String lastPlayed, String userRating) {

        static Expected of(NfoPlayback playback) {
            return new Expected(NfoSupplementWriter.toText(playback.playCount()),
                    NfoSupplementWriter.toText(playback.lastPlayed()),
                    NfoSupplementWriter.toText(playback.userRating()));
        }
    }
}
