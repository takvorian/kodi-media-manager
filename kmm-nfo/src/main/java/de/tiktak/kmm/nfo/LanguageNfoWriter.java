package de.tiktak.kmm.nfo;

import java.nio.file.Path;

/**
 * Schreibt nur die Sprache ({@code <language>}) in eine vorhandene Film- oder Serien-NFO, wie Ember sie dort führt.
 * Alles andere bleibt wie beim {@link TrailerNfoWriter} erhalten.
 */
public class LanguageNfoWriter {

    /**
     * @param language Sprache wie {@code de-DE} oder {@code null}, um den Eintrag zu entfernen
     * @throws NfoException wenn die NFO fehlt, keinen Film enthält oder nicht geschrieben werden kann
     */
    public void writeMovie(Path nfoFile, String language) {
        TrailerNfoWriter.writeElement(nfoFile, NfoElements.MOVIE, NfoElements.LANGUAGE, language);
    }

    /**
     * @param language Sprache wie {@code de-DE} oder {@code null}, um den Eintrag zu entfernen
     * @throws NfoException wenn die NFO fehlt, keine Serie enthält oder nicht geschrieben werden kann
     */
    public void writeTvShow(Path nfoFile, String language) {
        TrailerNfoWriter.writeElement(nfoFile, NfoElements.TV_SHOW, NfoElements.LANGUAGE, language);
    }
}
