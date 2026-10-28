package de.tiktak.kmm.tvshow;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Bearbeitbare Metadaten einer Serie, wie sie in die {@code tvshow.nfo} geschrieben werden.
 *
 * @param status z. B. {@code Continuing} oder {@code Ended}
 */
public record TvShowMetadata(
        @NotBlank @Size(max = TvTexts.MAX_TITLE_LENGTH) String title,
        @Size(max = TvTexts.MAX_TITLE_LENGTH) String originalTitle,
        @Size(max = TvTexts.MAX_TITLE_LENGTH) String sortTitle,
        @Min(MIN_YEAR) @Max(MAX_YEAR) Integer year,
        LocalDate premiered,
        @Size(max = TvTexts.MAX_PLOT_LENGTH) String plot,
        @PositiveOrZero Integer runtime,
        @Size(max = TvTexts.MAX_SHORT_LENGTH) String mpaa,
        @Size(max = TvTexts.MAX_SHORT_LENGTH) String status,
        @NotNull @Size(max = TvTexts.MAX_ENTRIES) List<@NotBlank @Size(max = TvTexts.MAX_GENRE_LENGTH) String> genres,
        @NotNull @Size(max = TvTexts.MAX_ENTRIES) List<@NotBlank @Size(max = TvTexts.MAX_LABEL_LENGTH) String> studios,
        @NotNull @Size(max = TvTexts.MAX_ENTRIES) List<@NotBlank @Size(max = TvTexts.MAX_LABEL_LENGTH) String> tags,
        @Pattern(regexp = NUMERIC_ID_PATTERN) String tvdbId,
        @Pattern(regexp = NUMERIC_ID_PATTERN) String tmdbId,
        @Pattern(regexp = IMDB_ID_PATTERN) String imdbId,
        @Min(MIN_USER_RATING) @Max(MAX_USER_RATING) Integer userRating) {

    static final int MIN_YEAR = 1870;
    static final int MAX_YEAR = 2200;
    static final int MIN_USER_RATING = 1;
    static final int MAX_USER_RATING = 10;
    static final String NUMERIC_ID_PATTERN = "[0-9]{1,10}";
    static final String IMDB_ID_PATTERN = "tt[0-9]{7,10}";

    public TvShowMetadata {
        title = TvTexts.clean(title, Integer.MAX_VALUE);
        originalTitle = TvTexts.clean(originalTitle, Integer.MAX_VALUE);
        sortTitle = TvTexts.clean(sortTitle, Integer.MAX_VALUE);
        plot = TvTexts.clean(plot, Integer.MAX_VALUE);
        mpaa = TvTexts.clean(mpaa, Integer.MAX_VALUE);
        status = TvTexts.clean(status, Integer.MAX_VALUE);
        tvdbId = TvTexts.clean(tvdbId, Integer.MAX_VALUE);
        tmdbId = TvTexts.clean(tmdbId, Integer.MAX_VALUE);
        imdbId = TvTexts.clean(imdbId, Integer.MAX_VALUE);
        genres = genres == null ? null : TvTexts.cleanAll(genres, Integer.MAX_VALUE, Integer.MAX_VALUE);
        studios = studios == null ? null : TvTexts.cleanAll(studios, Integer.MAX_VALUE, Integer.MAX_VALUE);
        tags = tags == null ? null : TvTexts.cleanAll(tags, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** Kopie mit anderen Tags. */
    public TvShowMetadata withTags(List<String> newTags) {
        return new TvShowMetadata(title, originalTitle, sortTitle, year, premiered, plot, runtime, mpaa, status, genres,
                studios, newTags, tvdbId, tmdbId, imdbId, userRating);
    }

    /**
     * Kopie für Daten aus Dateien und Fremdquellen, die die Grenzen der Datenbank einhält: Texte werden
     * gekürzt, ungültige Zahlen und IDs entfallen.
     */
    public TvShowMetadata withinLimits() {
        return new TvShowMetadata(
                TvTexts.clean(title, TvTexts.MAX_TITLE_LENGTH),
                TvTexts.clean(originalTitle, TvTexts.MAX_TITLE_LENGTH),
                TvTexts.clean(sortTitle, TvTexts.MAX_TITLE_LENGTH),
                year != null && year >= MIN_YEAR && year <= MAX_YEAR ? year : null,
                premiered,
                TvTexts.clean(plot, TvTexts.MAX_PLOT_LENGTH),
                runtime != null && runtime >= 0 ? runtime : null,
                TvTexts.clean(mpaa, TvTexts.MAX_SHORT_LENGTH),
                TvTexts.clean(status, TvTexts.MAX_SHORT_LENGTH),
                TvTexts.cleanAll(genres, TvTexts.MAX_GENRE_LENGTH, TvTexts.MAX_ENTRIES),
                TvTexts.cleanAll(studios, TvTexts.MAX_LABEL_LENGTH, TvTexts.MAX_ENTRIES),
                TvTexts.cleanAll(tags, TvTexts.MAX_LABEL_LENGTH, TvTexts.MAX_ENTRIES),
                TvTexts.id(tvdbId, NUMERIC_ID_PATTERN),
                TvTexts.id(tmdbId, NUMERIC_ID_PATTERN),
                TvTexts.id(imdbId, IMDB_ID_PATTERN),
                userRating != null && userRating >= MIN_USER_RATING && userRating <= MAX_USER_RATING ? userRating
                        : null);
    }
}
