package de.tiktak.kmm.nfo;

import java.nio.file.Path;

/**
 * Writes only the language ({@code <language>}) into an existing movie or TV show NFO, where Ember keeps it.
 * Everything else is kept, as with the {@link TrailerNfoWriter}.
 */
public class LanguageNfoWriter {

    /**
     * @param language language such as {@code de-DE}, or {@code null} to remove the entry
     * @throws NfoException if the NFO is missing, contains no movie or cannot be written
     */
    public void writeMovie(Path nfoFile, String language) {
        TrailerNfoWriter.writeElement(nfoFile, NfoElements.MOVIE, NfoElements.LANGUAGE, language);
    }

    /**
     * @param language language such as {@code de-DE}, or {@code null} to remove the entry
     * @throws NfoException if the NFO is missing, contains no TV show or cannot be written
     */
    public void writeTvShow(Path nfoFile, String language) {
        TrailerNfoWriter.writeElement(nfoFile, NfoElements.TV_SHOW, NfoElements.LANGUAGE, language);
    }
}
