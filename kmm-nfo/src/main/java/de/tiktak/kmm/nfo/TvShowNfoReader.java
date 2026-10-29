package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Liest {@code tvshow.nfo}-Dateien, auch in der älteren Schreibweise von Ember ({@code <id>} als
 * TVDB-ID, {@code <imdb>}, {@code <tmdb>}).
 */
public class TvShowNfoReader {

    private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

    /**
     * @throws NfoException wenn die Datei nicht gelesen werden kann oder keine Serien-NFO ist
     */
    public TvShowNfo read(Path nfoFile) {
        try {
            String content = Files.readString(nfoFile, StandardCharsets.UTF_8);
            Optional<String> xml = NfoDocuments.rootXml(content, NfoElements.TV_SHOW);
            if (xml.isEmpty()) {
                throw new IllegalArgumentException("no <tvshow> element");
            }
            return fromXml(NfoDocuments.parse(xml.get()).getDocumentElement());
        } catch (IOException | SAXException | RuntimeException e) {
            throw NfoException.unreadable(nfoFile, e);
        }
    }

    private static TvShowNfo fromXml(Element show) {
        Map<String, String> uniqueIds = new LinkedHashMap<>(legacyIds(show));
        uniqueIds.putAll(NfoReader.uniqueIds(show));
        return new TvShowNfo(
                NfoDocuments.text(show, NfoElements.TITLE),
                NfoDocuments.text(show, NfoElements.ORIGINAL_TITLE),
                NfoDocuments.text(show, NfoElements.SORT_TITLE),
                NfoReader.date(NfoDocuments.text(show, NfoElements.PREMIERED)),
                NfoDocuments.text(show, NfoElements.PLOT),
                NfoReader.number(NfoDocuments.text(show, NfoElements.RUNTIME)),
                NfoDocuments.text(show, NfoElements.MPAA),
                NfoDocuments.text(show, NfoElements.STATUS),
                NfoDocuments.texts(show, NfoElements.GENRE),
                NfoDocuments.texts(show, NfoElements.STUDIO),
                NfoDocuments.texts(show, NfoElements.TAG),
                uniqueIds,
                NfoSupplementReader.ratings(show),
                NfoSupplementReader.actors(show),
                seasonNames(show),
                NfoSupplementReader.integer(NfoDocuments.text(show, NfoElements.USER_RATING)),
                NfoSupplementReader.trailer(show));
    }

    private static Map<String, String> legacyIds(Element show) {
        Map<String, String> ids = new LinkedHashMap<>();
        String id = NfoDocuments.text(show, NfoElements.LEGACY_ID);
        if (id != null && NUMERIC.matcher(id).matches()) {
            ids.put(TvShowNfo.TVDB, id);
        }
        putIfPresent(ids, MovieNfo.IMDB, NfoDocuments.text(show, NfoElements.LEGACY_IMDB));
        putIfPresent(ids, MovieNfo.TMDB, NfoDocuments.text(show, NfoElements.LEGACY_TMDB));
        return ids;
    }

    private static TreeMap<Integer, String> seasonNames(Element show) {
        TreeMap<Integer, String> names = new TreeMap<>();
        for (Element season : NfoDocuments.children(show, NfoElements.NAMED_SEASON)) {
            Integer number = NfoSupplementReader.integer(season.getAttribute(NfoElements.NUMBER_ATTRIBUTE));
            String name = season.getTextContent().strip();
            if (number != null && !name.isEmpty()) {
                names.put(number, name);
            }
        }
        return names;
    }

    private static void putIfPresent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.put(type, value);
        }
    }
}
