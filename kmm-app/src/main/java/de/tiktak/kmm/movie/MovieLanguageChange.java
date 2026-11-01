package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.settings.KmmSettings;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inhalt von {@code POST /api/v1/movies/language}.
 *
 * @param language     Sprache wie {@code de-DE} oder {@code null}, dann gilt wieder die der Quelle bzw. der Einstellungen
 * @param refetchTexts ob Titel und Inhaltsangaben sofort in dieser Sprache neu geholt und überschrieben werden
 */
public record MovieLanguageChange(
        @NotNull @NotEmpty @Size(max = MAX_MOVIES) List<@NotNull Long> movieIds,
        @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String language,
        boolean refetchTexts) {

    static final int MAX_MOVIES = 20_000;
}
