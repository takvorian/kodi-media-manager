package de.tiktak.kmm.tvshow;

import java.util.List;

import de.tiktak.kmm.settings.KmmSettings;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/tvshows/language}.
 *
 * @param language     language such as {@code de-DE}, or {@code null} to use the source's or settings' language again
 * @param refetchTexts whether titles, plots and episode names are fetched and overwritten in this language immediately
 */
public record TvShowLanguageChange(
        @NotNull @NotEmpty @Size(max = MAX_SHOWS) List<@NotNull Long> showIds,
        @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String language,
        boolean refetchTexts) {

    static final int MAX_SHOWS = 20_000;
}
