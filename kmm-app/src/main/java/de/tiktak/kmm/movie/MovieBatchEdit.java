package de.tiktak.kmm.movie;

import java.util.LinkedHashSet;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import de.tiktak.kmm.filter.FieldChange;
import de.tiktak.kmm.filter.ListChange;

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
        return mpaa == null || mpaa.fits(MovieMetadata.MAX_MPAA_LENGTH);
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
}
