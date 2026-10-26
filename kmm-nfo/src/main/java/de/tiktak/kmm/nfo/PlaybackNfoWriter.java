package de.tiktak.kmm.nfo;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Schreibt nur Wiedergabezahl und letzte Wiedergabe in eine vorhandene NFO; alle anderen Elemente
 * bleiben unverändert. Eine fehlende NFO legt KMM dafür nicht an, denn Kodi hielte eine fast leere
 * NFO für vollständige Angaben.
 */
public class PlaybackNfoWriter {

    /**
     * @param playCount  Anzahl der Wiedergaben oder {@code null}, um die Angabe zu entfernen
     * @param lastPlayed letzte Wiedergabe oder {@code null}, um die Angabe zu entfernen
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, Integer playCount, LocalDateTime lastPlayed) {
        Element container = existing(nfoFile, NfoElements.MOVIE);
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
        apply(container.getOwnerDocument(), element, playCount, lastPlayed);
        NfoFileWriter.write(nfoFile, NfoDocuments.children(container, NfoElements.EPISODE_DETAILS));
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
