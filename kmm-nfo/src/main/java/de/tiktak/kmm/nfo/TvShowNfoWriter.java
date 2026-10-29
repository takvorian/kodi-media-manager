package de.tiktak.kmm.nfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Schreibt {@code tvshow.nfo}-Dateien. Nur die von KMM verwalteten Elemente werden ersetzt, alles
 * andere (Episodenführer, Bild-URLs …) bleibt erhalten. Die ältere ID-Schreibweise wird dabei durch
 * {@code <uniqueid>} ersetzt, damit keine widersprüchlichen IDs stehen bleiben.
 */
public class TvShowNfoWriter {

    public void write(Path nfoFile, TvShowNfo nfo) {
        Element container = NfoFileWriter.existingRoots(nfoFile, NfoElements.TV_SHOW)
                .orElseGet(NfoFileWriter::newContainer);
        Document document = container.getOwnerDocument();
        List<Element> shows = NfoDocuments.children(container, NfoElements.TV_SHOW);
        Element show = shows.isEmpty()
                ? NfoDomEditor.appendChild(document, container, NfoElements.TV_SHOW)
                : shows.getFirst();
        apply(document, show, nfo);
        NfoFileWriter.write(nfoFile, List.of(show));
    }

    private static void apply(Document document, Element show, TvShowNfo nfo) {
        NfoDomEditor.setText(document, show, NfoElements.TITLE, nfo.title());
        NfoDomEditor.setText(document, show, NfoElements.ORIGINAL_TITLE, nfo.originalTitle());
        NfoDomEditor.setText(document, show, NfoElements.SORT_TITLE, nfo.sortTitle());
        NfoDomEditor.setText(document, show, NfoElements.PREMIERED, NfoSupplementWriter.toText(nfo.premiered()));
        NfoDomEditor.setText(document, show, NfoElements.PLOT, nfo.plot());
        NfoDomEditor.setText(document, show, NfoElements.RUNTIME, NfoSupplementWriter.toText(nfo.runtime()));
        NfoDomEditor.setText(document, show, NfoElements.MPAA, nfo.mpaa());
        NfoDomEditor.setText(document, show, NfoElements.STATUS, nfo.status());
        NfoDomEditor.setTexts(document, show, NfoElements.GENRE, nfo.genres());
        NfoDomEditor.setTexts(document, show, NfoElements.STUDIO, nfo.studios());
        NfoDomEditor.setTexts(document, show, NfoElements.TAG, nfo.tags());
        NfoDomEditor.setUniqueIds(document, show, nfo.uniqueIds());
        for (String legacy : List.of(NfoElements.LEGACY_ID, NfoElements.LEGACY_IMDB, NfoElements.LEGACY_TMDB)) {
            NfoDocuments.children(show, legacy).forEach(show::removeChild);
        }
        NfoDomEditor.replaceAll(show, NfoElements.RATINGS, NfoSupplementWriter.ratings(document, nfo.ratings()));
        NfoDomEditor.setText(document, show, NfoElements.USER_RATING, NfoSupplementWriter.toText(nfo.userRating()));
        NfoDomEditor.replaceAll(show, NfoElements.NAMED_SEASON, seasonNames(document, nfo.seasonNames()));
        NfoSupplementWriter.replaceActors(document, show, nfo.actors());
        if (nfo.trailer() != null) {
            NfoDomEditor.setText(document, show, NfoElements.TRAILER, nfo.trailer());
        }
    }

    private static List<Element> seasonNames(Document document, Map<Integer, String> names) {
        return names.entrySet().stream().map(name -> {
            Element element = NfoDomEditor.textElement(document, NfoElements.NAMED_SEASON, name.getValue());
            element.setAttribute(NfoElements.NUMBER_ATTRIBUTE, name.getKey().toString());
            return element;
        }).toList();
    }
}
