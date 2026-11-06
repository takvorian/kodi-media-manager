package de.tiktak.kmm.movie;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request to rename movies of a source.
 *
 * @param movieIds the movies to rename; {@code null} means all of the source
 */
public record MovieRenameRequest(@NotNull @Valid MovieRenamePatterns patterns, List<Long> movieIds) {

    public MovieRenameRequest {
        movieIds = movieIds == null ? null : List.copyOf(movieIds);
    }
}
