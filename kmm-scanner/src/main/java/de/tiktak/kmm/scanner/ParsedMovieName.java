package de.tiktak.kmm.scanner;

/**
 * Movie title derived from a file or folder name.
 *
 * @param title  title, empty if the name contains no usable title
 * @param year   release year or {@code null}
 * @param imdbId IMDb ID like {@code tt0113277} or {@code null}
 */
public record ParsedMovieName(String title, Integer year, String imdbId) {

    public boolean hasTitle() {
        return !title.isBlank();
    }

    /** This name, but with the given ID if there is one. */
    ParsedMovieName withPreferredImdbId(String preferred) {
        return preferred == null ? this : new ParsedMovieName(title, year, preferred);
    }
}
