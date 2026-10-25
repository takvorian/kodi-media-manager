package de.tiktak.kmm.scanner;

/**
 * Aus einem Datei- oder Ordnernamen abgeleiteter Filmtitel.
 *
 * @param title Titel, leer wenn der Name keinen verwertbaren Titel enthält
 * @param year  Erscheinungsjahr oder {@code null}
 */
public record ParsedMovieName(String title, Integer year) {

    public boolean hasTitle() {
        return !title.isBlank();
    }
}
