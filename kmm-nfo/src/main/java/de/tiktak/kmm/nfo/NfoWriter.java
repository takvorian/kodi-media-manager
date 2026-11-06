package de.tiktak.kmm.nfo;

import java.nio.file.Path;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Writes Kodi movie NFOs. If an NFO already exists, only the elements managed by KMM are replaced; everything else
 * (trailer, image URLs …) is kept. Without known further details ({@link MovieNfo#supplement()}) actors, ratings
 * and stream details stay unchanged as well; KMM only replaces stream details if it knows any.
 */
public class NfoWriter {

    public void write(Path nfoFile, MovieNfo nfo) {
        Element container = NfoFileWriter.existingRoots(nfoFile, NfoElements.MOVIE)
                .orElseGet(NfoFileWriter::newContainer);
        List<Element> movies = NfoDocuments.children(container, NfoElements.MOVIE);
        Element movie = movies.isEmpty()
                ? NfoDomEditor.appendChild(container.getOwnerDocument(), container, NfoElements.MOVIE)
                : movies.getFirst();
        apply(container.getOwnerDocument(), movie, nfo);
        NfoFileWriter.write(nfoFile, List.of(movie));
    }

    private static void apply(Document document, Element movie, MovieNfo nfo) {
        NfoDomEditor.setText(document, movie, NfoElements.TITLE, nfo.title());
        NfoDomEditor.setText(document, movie, NfoElements.ORIGINAL_TITLE, nfo.originalTitle());
        NfoDomEditor.setText(document, movie, NfoElements.SORT_TITLE, nfo.sortTitle());
        NfoDomEditor.setText(document, movie, NfoElements.YEAR, toText(nfo.effectiveYear()));
        NfoDomEditor.setText(document, movie, NfoElements.PREMIERED, toText(nfo.premiered()));
        NfoDomEditor.setText(document, movie, NfoElements.PLOT, nfo.plot());
        NfoDomEditor.setText(document, movie, NfoElements.OUTLINE, nfo.outline());
        NfoDomEditor.setText(document, movie, NfoElements.TAGLINE, nfo.tagline());
        NfoDomEditor.setText(document, movie, NfoElements.RUNTIME, toText(nfo.runtime()));
        NfoDomEditor.setText(document, movie, NfoElements.MPAA, nfo.mpaa());
        NfoDomEditor.setText(document, movie, NfoElements.EDITION, nfo.edition());
        NfoDomEditor.setText(document, movie, NfoElements.VIDEO_SOURCE, nfo.videoSource());
        NfoDomEditor.setTexts(document, movie, NfoElements.GENRE, nfo.genres());
        NfoDomEditor.setUniqueIds(document, movie, nfo.uniqueIds());
        if (nfo.supplement() != null) {
            NfoSupplementWriter.write(document, movie, nfo.supplement());
        }
    }

    private static String toText(Object value) {
        return value == null ? null : value.toString();
    }
}
