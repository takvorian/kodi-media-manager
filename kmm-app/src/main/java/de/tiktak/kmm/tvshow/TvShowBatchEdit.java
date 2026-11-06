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
 * Common changes to several TV shows. Parts not specified stay unchanged.
 *
 * @param showIds the TV shows to change
 * @param genres  add or remove genres
 * @param tags    add or remove tags
 * @param mpaa    set or clear the age rating
 * @param status  set or clear the status, e.g. {@code Ended}
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

    /** Certification and status are shorter than the other fields. */
    @AssertTrue(message = "Certification or status is too long.")
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
