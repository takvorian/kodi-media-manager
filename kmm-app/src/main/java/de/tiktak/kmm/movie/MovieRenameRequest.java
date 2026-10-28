package de.tiktak.kmm.movie;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Auftrag, Filme einer Quelle umzubenennen.
 *
 * @param movieIds die umzubenennenden Filme; {@code null} heißt alle der Quelle
 */
public record MovieRenameRequest(@NotNull @Valid MovieRenamePatterns patterns, List<Long> movieIds) {

    public MovieRenameRequest {
        movieIds = movieIds == null ? null : List.copyOf(movieIds);
    }
}
