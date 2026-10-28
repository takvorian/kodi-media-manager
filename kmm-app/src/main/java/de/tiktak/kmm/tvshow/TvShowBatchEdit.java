package de.tiktak.kmm.tvshow;

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
 * Gemeinsame Änderungen an mehreren Serien. Nicht angegebene Teile bleiben unverändert.
 *
 * @param showIds die zu ändernden Serien
 * @param genres  Genres hinzufügen bzw. entfernen
 * @param tags    Tags hinzufügen bzw. entfernen
 * @param mpaa    Altersfreigabe setzen oder leeren
 * @param status  Status setzen oder leeren, z. B. {@code Ended}
 */
public record TvShowBatchEdit(
        @NotNull @NotEmpty @Size(max = MAX_SHOWS) List<@NotNull Long> showIds,
        @Valid ListChange genres,
        @Valid ListChange tags,
        @Valid FieldChange mpaa,
        @Valid FieldChange status) {

    static final int MAX_SHOWS = 5_000;

    public TvShowBatchEdit {
        showIds = showIds == null ? null : List.copyOf(new LinkedHashSet<>(showIds));
        genres = genres == null ? ListChange.NONE : genres;
        tags = tags == null ? ListChange.NONE : tags;
        mpaa = mpaa == null ? FieldChange.KEEP : mpaa;
        status = status == null ? FieldChange.KEEP : status;
    }

    /** Altersfreigabe und Status sind kürzer als die übrigen Felder. */
    @AssertTrue(message = "Altersfreigabe oder Status sind zu lang.")
    boolean isShortEnough() {
        return (mpaa == null || mpaa.fits(TvTexts.MAX_SHORT_LENGTH))
                && (status == null || status.fits(TvTexts.MAX_SHORT_LENGTH));
    }

    TvShowMetadata apply(TvShowMetadata metadata) {
        return new TvShowMetadata(metadata.title(), metadata.originalTitle(), metadata.sortTitle(), metadata.year(),
                metadata.premiered(), metadata.plot(), metadata.runtime(), mpaa.apply(metadata.mpaa()),
                status.apply(metadata.status()), genres.apply(metadata.genres()), metadata.studios(),
                tags.apply(metadata.tags()), metadata.tvdbId(), metadata.tmdbId(), metadata.imdbId(),
                metadata.userRating());
    }
}
