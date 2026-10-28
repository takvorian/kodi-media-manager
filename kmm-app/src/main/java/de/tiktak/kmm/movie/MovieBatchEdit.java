package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Gemeinsame Änderungen an mehreren Filmen. Nicht angegebene Teile bleiben unverändert.
 *
 * @param movieIds    die zu ändernden Filme
 * @param genres      Genres hinzufügen bzw. entfernen
 * @param tags        Tags hinzufügen bzw. entfernen
 * @param mpaa        Altersfreigabe setzen oder leeren
 * @param edition     Fassung setzen oder leeren
 * @param videoSource Videoquelle setzen oder leeren
 */
public record MovieBatchEdit(
        @NotNull @NotEmpty @Size(max = MAX_MOVIES) List<@NotNull Long> movieIds,
        @Valid ListChange genres,
        @Valid ListChange tags,
        @Valid FieldChange mpaa,
        @Valid FieldChange edition,
        @Valid FieldChange videoSource) {

    static final int MAX_MOVIES = 20_000;
    static final int MAX_ENTRIES = 50;

    public MovieBatchEdit {
        movieIds = movieIds == null ? null : List.copyOf(new LinkedHashSet<>(movieIds));
        genres = genres == null ? ListChange.NONE : genres;
        tags = tags == null ? ListChange.NONE : tags;
        mpaa = mpaa == null ? FieldChange.KEEP : mpaa;
        edition = edition == null ? FieldChange.KEEP : edition;
        videoSource = videoSource == null ? FieldChange.KEEP : videoSource;
    }

    /** Die Altersfreigabe ist kürzer als die übrigen Felder. */
    @AssertTrue(message = "Die Altersfreigabe ist zu lang.")
    boolean isMpaaShortEnough() {
        return mpaa == null || mpaa.value() == null || mpaa.value().length() <= MovieMetadata.MAX_MPAA_LENGTH;
    }

    boolean changesMetadata() {
        return genres.changes() || mpaa.changes() || edition.changes() || videoSource.changes();
    }

    /** Die Metadaten nach den Änderungen; Genres, Freigabe, Fassung und Videoquelle, sonst alles wie bisher. */
    MovieMetadata apply(MovieMetadata metadata) {
        return new MovieMetadata(metadata.title(), metadata.originalTitle(), metadata.sortTitle(), metadata.year(),
                metadata.premiered(), metadata.plot(), metadata.outline(), metadata.tagline(), metadata.runtime(),
                mpaa.apply(metadata.mpaa()), genres.apply(metadata.genres()), metadata.imdbId(), metadata.tmdbId(),
                edition.apply(metadata.edition()), videoSource.apply(metadata.videoSource()));
    }

    /**
     * Einträge einer Liste hinzufügen und entfernen; ohne Rücksicht auf Groß- und Kleinschreibung, vorhandene
     * Einträge behalten ihre Schreibweise.
     */
    public record ListChange(
            @Size(max = MAX_ENTRIES) List<@NotNull @Size(max = MovieSupplement.MAX_LABEL_LENGTH) String> add,
            @Size(max = MAX_ENTRIES) List<@NotNull String> remove) {

        static final ListChange NONE = new ListChange(List.of(), List.of());

        public ListChange {
            add = clean(add);
            remove = clean(remove);
        }

        boolean changes() {
            return !add.isEmpty() || !remove.isEmpty();
        }

        List<String> apply(List<String> current) {
            Set<String> removed = remove.stream().map(entry -> entry.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            List<String> result = new ArrayList<>(current.stream()
                    .filter(entry -> !removed.contains(entry.toLowerCase(Locale.ROOT)))
                    .toList());
            for (String entry : add) {
                if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(entry))) {
                    result.add(entry);
                }
            }
            return List.copyOf(result);
        }

        private static List<String> clean(List<String> entries) {
            return entries == null ? List.of() : entries.stream()
                    .map(String::strip)
                    .filter(entry -> !entry.isEmpty())
                    .distinct()
                    .toList();
        }
    }

    /**
     * Ein Feld setzen ({@code value}) oder leeren ({@code clear}); beides fehlt: unverändert.
     */
    public record FieldChange(Boolean clear, @Size(max = MovieMetadata.MAX_LABEL_LENGTH) String value) {

        static final FieldChange KEEP = new FieldChange(false, null);

        public FieldChange {
            clear = Boolean.TRUE.equals(clear);
            value = value == null || value.isBlank() ? null : value.strip();
        }

        boolean changes() {
            return clear || value != null;
        }

        String apply(String current) {
            return Boolean.TRUE.equals(clear) ? null : value != null ? value : current;
        }
    }
}
