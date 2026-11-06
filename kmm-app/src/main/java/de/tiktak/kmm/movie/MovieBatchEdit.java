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
 * Common changes to several movies. Parts not given stay unchanged.
 *
 * @param movieIds    the movies to change
 * @param genres      genres to add or remove
 * @param tags        tags to add or remove
 * @param mpaa        set or clear the certification
 * @param edition     set or clear the edition
 * @param videoSource set or clear the video source
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

    /** The certification is shorter than the other fields. */
    @AssertTrue(message = "The certification is too long.")
    boolean isMpaaShortEnough() {
        return mpaa == null || mpaa.fits(MovieMetadata.MAX_MPAA_LENGTH);
    }

    boolean changesMetadata() {
        return genres.changes() || mpaa.changes() || edition.changes() || videoSource.changes();
    }

    /** The metadata after the changes: genres, certification, edition and video source, everything else as before. */
    MovieMetadata apply(MovieMetadata metadata) {
        return new MovieMetadata(metadata.title(), metadata.originalTitle(), metadata.sortTitle(), metadata.year(),
                metadata.premiered(), metadata.plot(), metadata.outline(), metadata.tagline(), metadata.runtime(),
                mpaa.apply(metadata.mpaa()), genres.apply(metadata.genres()), metadata.imdbId(), metadata.tmdbId(),
                edition.apply(metadata.edition()), videoSource.apply(metadata.videoSource()));
    }
}
