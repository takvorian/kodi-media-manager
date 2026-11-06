package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Reads episode NFOs. For multi-episode files the file contains several {@code <episodedetails>}.
 */
public class EpisodeNfoReader {

    /**
     * @return the episodes of the file in their order, at least one
     * @throws NfoException if the file cannot be read or is not an episode NFO
     */
    public List<EpisodeNfo> read(Path nfoFile) {
        try {
            String content = Files.readString(nfoFile, StandardCharsets.UTF_8);
            Optional<String> xml = NfoDocuments.rootXml(content, NfoElements.EPISODE_DETAILS);
            if (xml.isEmpty()) {
                throw new IllegalArgumentException("no <episodedetails> element");
            }
            Element container = NfoDocuments.parseAll(xml.get()).getDocumentElement();
            return NfoDocuments.children(container, NfoElements.EPISODE_DETAILS).stream()
                    .map(EpisodeNfoReader::fromXml)
                    .toList();
        } catch (IOException | SAXException | RuntimeException e) {
            throw NfoException.unreadable(nfoFile, e);
        }
    }

    static EpisodeNfo fromXml(Element episode) {
        return new EpisodeNfo(
                NfoDocuments.text(episode, NfoElements.TITLE),
                NfoDocuments.text(episode, NfoElements.ORIGINAL_TITLE),
                NfoSupplementReader.integer(NfoDocuments.text(episode, NfoElements.SEASON)),
                NfoSupplementReader.integer(NfoDocuments.text(episode, NfoElements.EPISODE)),
                NfoDocuments.text(episode, NfoElements.PLOT),
                NfoReader.date(NfoDocuments.text(episode, NfoElements.AIRED)),
                NfoReader.number(NfoDocuments.text(episode, NfoElements.RUNTIME)),
                NfoDocuments.text(episode, NfoElements.VIDEO_SOURCE),
                NfoReader.uniqueIds(episode),
                NfoSupplementReader.ratings(episode),
                NfoDocuments.texts(episode, NfoElements.DIRECTOR),
                NfoDocuments.texts(episode, NfoElements.CREDITS),
                NfoSupplementReader.actors(episode),
                NfoSupplementReader.integer(NfoDocuments.text(episode, NfoElements.USER_RATING)),
                NfoSupplementReader.integer(NfoDocuments.text(episode, NfoElements.PLAY_COUNT)),
                NfoSupplementReader.dateTime(NfoDocuments.text(episode, NfoElements.LAST_PLAYED)),
                NfoSupplementReader.dateTime(NfoDocuments.text(episode, NfoElements.DATE_ADDED)),
                NfoStreamDetails.read(episode));
    }
}
