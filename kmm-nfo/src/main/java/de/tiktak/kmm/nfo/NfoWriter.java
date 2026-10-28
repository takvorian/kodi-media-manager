package de.tiktak.kmm.nfo;

import java.nio.file.Path;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Schreibt Kodi-Film-NFOs. Ist bereits eine NFO vorhanden, werden nur die von KMM verwalteten
 * Elemente ersetzt; alles andere (Trailer, Bild-URLs …) bleibt erhalten. Ohne bekannte weitere Angaben
 * ({@link MovieNfo#supplement()}) bleiben auch Darsteller, Bewertungen und Stream-Details unverändert;
 * Stream-Details ersetzt KMM nur, wenn es welche kennt.
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
