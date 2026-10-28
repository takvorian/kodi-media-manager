package de.tiktak.kmm.movie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Bearbeitbare Angaben eines Filmsets. Leere Texte gelten als nicht gesetzt.
 *
 * @param tmdbCollectionId TMDB-ID der Sammlung oder {@code null}
 */
public record MovieSetData(
        @NotBlank @Size(max = MovieSupplement.MAX_NAME_LENGTH) String name,
        @Size(max = MAX_OVERVIEW_LENGTH) String overview,
        @Pattern(regexp = "[0-9]{1,10}") String tmdbCollectionId,
        @NotNull SetSortMethod sortMethod) {

    static final int MAX_OVERVIEW_LENGTH = 20_000;

    public MovieSetData {
        name = name == null ? null : name.strip();
        overview = blankToNull(overview);
        tmdbCollectionId = blankToNull(tmdbCollectionId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
