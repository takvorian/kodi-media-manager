package de.tiktak.kmm.tvshow;

import java.util.List;

import de.tiktak.kmm.settings.KmmSettings;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inhalt von {@code POST /api/v1/tvshows/language}.
 *
 * @param language     Sprache wie {@code de-DE} oder {@code null}, dann gilt wieder die der Quelle bzw. der Einstellungen
 * @param refetchTexts ob Titel, Inhaltsangaben und Episodennamen sofort in dieser Sprache geholt und überschrieben werden
 */
public record TvShowLanguageChange(
        @NotNull @NotEmpty @Size(max = MAX_SHOWS) List<@NotNull Long> showIds,
        @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String language,
        boolean refetchTexts) {

    static final int MAX_SHOWS = 20_000;
}
