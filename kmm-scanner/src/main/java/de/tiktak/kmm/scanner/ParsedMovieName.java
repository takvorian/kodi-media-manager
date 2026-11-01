package de.tiktak.kmm.scanner;

/**
 * Aus einem Datei- oder Ordnernamen abgeleiteter Filmtitel.
 *
 * @param title  Titel, leer wenn der Name keinen verwertbaren Titel enthält
 * @param year   Erscheinungsjahr oder {@code null}
 * @param imdbId IMDb-ID wie {@code tt0113277} oder {@code null}
 */
public record ParsedMovieName(String title, Integer year, String imdbId) {

    public boolean hasTitle() {
        return !title.isBlank();
    }

    /** Dieser Name, aber mit der übergebenen ID, wenn es eine gibt. */
    ParsedMovieName withPreferredImdbId(String preferred) {
        return preferred == null ? this : new ParsedMovieName(title, year, preferred);
    }
}
