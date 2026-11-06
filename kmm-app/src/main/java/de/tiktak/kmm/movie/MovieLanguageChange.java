package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.settings.KmmSettings;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Content of {@code POST /api/v1/movies/language}.
 *
 * @param language     language such as {@code de-DE}, or {@code null}, in which case the source or settings
 *                     language applies again
 * @param refetchTexts whether titles and plot summaries are immediately re-fetched in this language and overwritten
 */
public record MovieLanguageChange(
        @NotNull @NotEmpty @Size(max = MAX_MOVIES) List<@NotNull Long> movieIds,
        @Pattern(regexp = KmmSettings.METADATA_LANGUAGE_PATTERN) String language,
        boolean refetchTexts) {

    static final int MAX_MOVIES = 20_000;
}
