package de.tiktak.kmm.nfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Schreibt Episoden-NFOs. Jede übergebene Episode ersetzt das {@code <episodedetails>} mit derselben
 * Staffel und Episode oder wird angehängt; andere Episoden einer Mehrfachepisode und nicht von KMM
 * verwaltete Elemente (Bild-URLs …) bleiben erhalten. Stream-Details ersetzt KMM, sofern es welche kennt.
 */
public class EpisodeNfoWriter {

    /**
     * @param episodes zu schreibende Episoden; jede braucht Staffel und Episode
     */
    public void write(Path nfoFile, List<EpisodeNfo> episodes) {
        if (episodes.stream().anyMatch(episode -> episode.season() == null || episode.episode() == null)) {
            throw new IllegalArgumentException("episodes need season and episode numbers");
        }
        Element container = NfoFileWriter.existingRoots(nfoFile, NfoElements.EPISODE_DETAILS)
                .orElseGet(NfoFileWriter::newContainer);
        Document document = container.getOwnerDocument();
        for (EpisodeNfo episode : episodes) {
            Element element = existing(container, episode.season(), episode.episode())
                    .orElseGet(() -> NfoDomEditor.appendChild(document, container, NfoElements.EPISODE_DETAILS));
            apply(document, element, episode);
        }
        NfoFileWriter.write(nfoFile, NfoDocuments.children(container, NfoElements.EPISODE_DETAILS));
    }

    /** Das {@code <episodedetails>} der Episode innerhalb einer gelesenen NFO. */
    static Optional<Element> existing(Element container, int season, int episode) {
        List<Element> elements = NfoDocuments.children(container, NfoElements.EPISODE_DETAILS);
        Optional<Element> matching = elements.stream()
                .filter(element -> EpisodeNfoReader.fromXml(element).describes(season, episode))
                .findFirst();
        if (matching.isPresent()) {
            return matching;
        }
        // Eine einzelne NFO ohne Staffel und Episode gehört zu der Episode der Datei.
        boolean singleWithoutNumbers = elements.size() == 1
                && EpisodeNfoReader.fromXml(elements.getFirst()).episode() == null;
        return singleWithoutNumbers ? Optional.of(elements.getFirst()) : Optional.empty();
    }

    private static void apply(Document document, Element element, EpisodeNfo nfo) {
        NfoDomEditor.setText(document, element, NfoElements.TITLE, nfo.title());
        NfoDomEditor.setText(document, element, NfoElements.ORIGINAL_TITLE, nfo.originalTitle());
        NfoDomEditor.setText(document, element, NfoElements.SEASON, NfoSupplementWriter.toText(nfo.season()));
        NfoDomEditor.setText(document, element, NfoElements.EPISODE, NfoSupplementWriter.toText(nfo.episode()));
        NfoDomEditor.setText(document, element, NfoElements.PLOT, nfo.plot());
        NfoDomEditor.setText(document, element, NfoElements.AIRED, NfoSupplementWriter.toText(nfo.aired()));
        NfoDomEditor.setText(document, element, NfoElements.RUNTIME, NfoSupplementWriter.toText(nfo.runtime()));
        NfoDomEditor.setText(document, element, NfoElements.VIDEO_SOURCE, nfo.videoSource());
        NfoDomEditor.setUniqueIds(document, element, nfo.uniqueIds());
        NfoDomEditor.replaceAll(element, NfoElements.RATINGS, NfoSupplementWriter.ratings(document, nfo.ratings()));
        NfoDomEditor.setText(document, element, NfoElements.USER_RATING, NfoSupplementWriter.toText(nfo.userRating()));
        NfoDomEditor.setText(document, element, NfoElements.PLAY_COUNT, NfoSupplementWriter.toText(nfo.playCount()));
        NfoDomEditor.setText(document, element, NfoElements.LAST_PLAYED,
                NfoSupplementWriter.toText(nfo.lastPlayed()));
        NfoDomEditor.setText(document, element, NfoElements.DATE_ADDED, NfoSupplementWriter.toText(nfo.dateAdded()));
        NfoDomEditor.setTexts(document, element, NfoElements.DIRECTOR, nfo.directors());
        NfoDomEditor.setTexts(document, element, NfoElements.CREDITS, nfo.writers());
        NfoDomEditor.replaceAll(element, NfoElements.ACTOR,
                nfo.actors().stream().map(actor -> NfoSupplementWriter.actor(document, actor)).toList());
        NfoStreamDetails.write(document, element, nfo.streams());
    }
}
