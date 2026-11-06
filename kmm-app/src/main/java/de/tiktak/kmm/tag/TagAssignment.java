package de.tiktak.kmm.tag;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A tag and the movies and TV shows it is to be assigned to or removed from.
 */
public record TagAssignment(
        @NotBlank @Size(max = TagNames.MAX_LENGTH) String tag,
        @NotNull List<Long> movieIds,
        @NotNull List<Long> showIds) {

    public TagAssignment {
        movieIds = movieIds == null ? null : List.copyOf(movieIds);
        showIds = showIds == null ? null : List.copyOf(showIds);
    }
}
