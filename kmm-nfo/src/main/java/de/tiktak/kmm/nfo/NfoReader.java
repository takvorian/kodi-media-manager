package de.tiktak.kmm.nfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Liest Kodi-Film-NFOs. Unterstützt neben dem XML-Format auch reine URL-NFOs (nur ein Link auf
 * IMDb oder TMDB) und kombinierte NFOs (XML gefolgt von einem Link), wie Kodi sie akzeptiert.
 */
public class NfoReader {

    private static final Pattern IMDB_ID = Pattern.compile("\\btt[0-9]{7,10}\\b");
    private static final Pattern TMDB_URL = Pattern.compile("themoviedb\\.org/movie/([0-9]+)");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+");
    private static final Pattern YEAR = Pattern.compile("[0-9]{4}");
    private static final int TMDB_ID_GROUP = 1;
    private static final int DATE_LENGTH = 10;

    public MovieNfo read(Path nfoFile) {
        try {
            String content = Files.readString(nfoFile, StandardCharsets.UTF_8);
            return parse(content);
        } catch (IOException | SAXException | RuntimeException e) {
            throw NfoException.unreadable(nfoFile, e);
        }
    }

    private MovieNfo parse(String content) throws IOException, SAXException {
        Map<String, String> urlIds = idsFromUrls(NfoDocuments.outsideMovieXml(content));
        Optional<String> xml = NfoDocuments.movieXml(content);
        if (xml.isEmpty()) {
            return new MovieNfo(null, null, null, null, null, null, null, null, null, null, null, null, List.of(), urlIds,
                    MovieNfoSupplement.EMPTY);
        }
        Document document = NfoDocuments.parse(xml.get());
        return fromXml(document.getDocumentElement(), urlIds);
    }

    private MovieNfo fromXml(Element movie, Map<String, String> urlIds) {
        Map<String, String> uniqueIds = new LinkedHashMap<>(urlIds);
        uniqueIds.putAll(legacyIds(movie));
        uniqueIds.putAll(uniqueIds(movie));
        return new MovieNfo(
                NfoDocuments.text(movie, NfoElements.TITLE),
                NfoDocuments.text(movie, NfoElements.ORIGINAL_TITLE),
                NfoDocuments.text(movie, NfoElements.SORT_TITLE),
                year(NfoDocuments.text(movie, NfoElements.YEAR)),
                date(NfoDocuments.text(movie, NfoElements.PREMIERED)),
                NfoDocuments.text(movie, NfoElements.PLOT),
                NfoDocuments.text(movie, NfoElements.OUTLINE),
                NfoDocuments.text(movie, NfoElements.TAGLINE),
                number(NfoDocuments.text(movie, NfoElements.RUNTIME)),
                NfoDocuments.text(movie, NfoElements.MPAA),
                NfoDocuments.text(movie, NfoElements.EDITION),
                NfoDocuments.text(movie, NfoElements.VIDEO_SOURCE),
                NfoDocuments.texts(movie, NfoElements.GENRE),
                uniqueIds,
                NfoSupplementReader.read(movie),
                NfoDocuments.text(movie, NfoElements.LANGUAGE));
    }

    static Map<String, String> uniqueIds(Element movie) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (Element uniqueId : NfoDocuments.children(movie, NfoElements.UNIQUE_ID)) {
            String type = uniqueId.getAttribute(NfoElements.TYPE_ATTRIBUTE).strip().toLowerCase(Locale.ROOT);
            String value = uniqueId.getTextContent().strip();
            if (!type.isEmpty() && !value.isEmpty()) {
                ids.put(type, value);
            }
        }
        return ids;
    }

    private static Map<String, String> legacyIds(Element movie) {
        Map<String, String> ids = new LinkedHashMap<>();
        String id = NfoDocuments.text(movie, NfoElements.LEGACY_ID);
        if (id != null && IMDB_ID.matcher(id).matches()) {
            ids.put(MovieNfo.IMDB, id);
        }
        putIfPresent(ids, MovieNfo.IMDB, NfoDocuments.text(movie, NfoElements.LEGACY_IMDB_ID));
        putIfPresent(ids, MovieNfo.TMDB, NfoDocuments.text(movie, NfoElements.LEGACY_TMDB_ID));
        return ids;
    }

    private static Map<String, String> idsFromUrls(String outsideXml) {
        Map<String, String> ids = new LinkedHashMap<>();
        Matcher imdb = IMDB_ID.matcher(outsideXml);
        if (imdb.find()) {
            ids.put(MovieNfo.IMDB, imdb.group());
        }
        Matcher tmdb = TMDB_URL.matcher(outsideXml);
        if (tmdb.find()) {
            ids.put(MovieNfo.TMDB, tmdb.group(TMDB_ID_GROUP));
        }
        return ids;
    }

    private static void putIfPresent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.put(type, value);
        }
    }

    static Integer year(String value) {
        return value != null && YEAR.matcher(value).matches() ? Integer.valueOf(value) : null;
    }

    /** Kodi schreibt die Laufzeit in Minuten, ältere Scraper teils als „120 min“. */
    static Integer number(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    static LocalDate date(String value) {
        if (value == null || value.length() < DATE_LENGTH) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, DATE_LENGTH));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
