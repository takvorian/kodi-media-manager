package de.tiktak.kmm.tvshow;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Bearbeitbare Metadaten einer Episode. Staffel und Episode stehen im Dateinamen und sind hier
 * nicht änderbar.
 */
public record EpisodeMetadata(
        @NotBlank @Size(max = TvTexts.MAX_TITLE_LENGTH) String title,
        @Size(max = TvTexts.MAX_TITLE_LENGTH) String originalTitle,
        @Size(max = TvTexts.MAX_PLOT_LENGTH) String plot,
        LocalDate aired,
        @PositiveOrZero Integer runtime,
        @Pattern(regexp = TvShowMetadata.NUMERIC_ID_PATTERN) String tvdbId,
        @Pattern(regexp = TvShowMetadata.NUMERIC_ID_PATTERN) String tmdbId,
        @Pattern(regexp = TvShowMetadata.IMDB_ID_PATTERN) String imdbId,
        @Min(TvShowMetadata.MIN_USER_RATING) @Max(TvShowMetadata.MAX_USER_RATING) Integer userRating) {

    public EpisodeMetadata {
        title = TvTexts.clean(title, Integer.MAX_VALUE);
        originalTitle = TvTexts.clean(originalTitle, Integer.MAX_VALUE);
        plot = TvTexts.clean(plot, Integer.MAX_VALUE);
        tvdbId = TvTexts.clean(tvdbId, Integer.MAX_VALUE);
        tmdbId = TvTexts.clean(tmdbId, Integer.MAX_VALUE);
        imdbId = TvTexts.clean(imdbId, Integer.MAX_VALUE);
    }

    /** Kopie, die die Grenzen der Datenbank einhält (für Daten aus Dateien und Fremdquellen). */
    public EpisodeMetadata withinLimits() {
        return new EpisodeMetadata(
                TvTexts.clean(title, TvTexts.MAX_TITLE_LENGTH),
                TvTexts.clean(originalTitle, TvTexts.MAX_TITLE_LENGTH),
                TvTexts.clean(plot, TvTexts.MAX_PLOT_LENGTH),
                aired,
                runtime != null && runtime >= 0 ? runtime : null,
                TvTexts.id(tvdbId, TvShowMetadata.NUMERIC_ID_PATTERN),
                TvTexts.id(tmdbId, TvShowMetadata.NUMERIC_ID_PATTERN),
                TvTexts.id(imdbId, TvShowMetadata.IMDB_ID_PATTERN),
                userRating != null && userRating >= TvShowMetadata.MIN_USER_RATING
                        && userRating <= TvShowMetadata.MAX_USER_RATING ? userRating : null);
    }
}
