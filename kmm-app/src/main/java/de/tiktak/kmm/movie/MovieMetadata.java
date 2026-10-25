package de.tiktak.kmm.movie;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Bearbeitbare Metadaten eines Films. Entspricht den Feldern, die KMM in die NFO schreibt.
 * Nicht gesetzte Werte sind {@code null}.
 */
public record MovieMetadata(
        @NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
        @Size(max = MAX_TITLE_LENGTH) String originalTitle,
        @Size(max = MAX_TITLE_LENGTH) String sortTitle,
        @Min(MIN_YEAR) @Max(MAX_YEAR) Integer year,
        LocalDate premiered,
        @Size(max = MAX_PLOT_LENGTH) String plot,
        @Size(max = MAX_OUTLINE_LENGTH) String outline,
        @Size(max = MAX_TITLE_LENGTH) String tagline,
        @PositiveOrZero Integer runtime,
        @Size(max = MAX_MPAA_LENGTH) String mpaa,
        @NotNull @Size(max = MAX_GENRES) List<@NotBlank @Size(max = MAX_GENRE_LENGTH) String> genres,
        @Pattern(regexp = IMDB_ID_PATTERN) String imdbId,
        @Pattern(regexp = TMDB_ID_PATTERN) String tmdbId) {

    static final int MAX_TITLE_LENGTH = 512;
    static final int MAX_OUTLINE_LENGTH = 2048;
    static final int MAX_PLOT_LENGTH = 20_000;
    static final int MAX_MPAA_LENGTH = 64;
    static final int MAX_GENRES = 50;
    static final int MAX_GENRE_LENGTH = 128;
    static final int MIN_YEAR = 1870;
    static final int MAX_YEAR = 2200;
    static final String IMDB_ID_PATTERN = "tt[0-9]{7,10}";
    static final String TMDB_ID_PATTERN = "[0-9]{1,10}";

    public MovieMetadata {
        title = trimToNull(title);
        originalTitle = trimToNull(originalTitle);
        sortTitle = trimToNull(sortTitle);
        plot = trimToNull(plot);
        outline = trimToNull(outline);
        tagline = trimToNull(tagline);
        mpaa = trimToNull(mpaa);
        imdbId = trimToNull(imdbId);
        tmdbId = trimToNull(tmdbId);
        genres = genres == null ? null : genres.stream()
                .map(MovieMetadata::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Kopie für Daten aus Fremdquellen, die die Grenzen der Datenbank einhält: Texte werden gekürzt,
     * ungültige Jahre, Laufzeiten und IDs entfallen.
     */
    public MovieMetadata withinLimits() {
        return new MovieMetadata(
                Texts.clean(title, MAX_TITLE_LENGTH),
                Texts.clean(originalTitle, MAX_TITLE_LENGTH),
                Texts.clean(sortTitle, MAX_TITLE_LENGTH),
                year != null && year >= MIN_YEAR && year <= MAX_YEAR ? year : null,
                premiered,
                Texts.clean(plot, MAX_PLOT_LENGTH),
                Texts.clean(outline, MAX_OUTLINE_LENGTH),
                Texts.clean(tagline, MAX_TITLE_LENGTH),
                runtime != null && runtime >= 0 ? runtime : null,
                Texts.clean(mpaa, MAX_MPAA_LENGTH),
                genres == null ? List.of() : genres.stream()
                        .map(genre -> Texts.clean(genre, MAX_GENRE_LENGTH))
                        .filter(Objects::nonNull)
                        .distinct()
                        .limit(MAX_GENRES)
                        .toList(),
                imdbId != null && imdbId.matches(IMDB_ID_PATTERN) ? imdbId : null,
                tmdbId != null && tmdbId.matches(TMDB_ID_PATTERN) ? tmdbId : null);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
