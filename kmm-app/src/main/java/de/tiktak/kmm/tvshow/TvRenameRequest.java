package de.tiktak.kmm.tvshow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Auftrag, Serien einer Quelle umzubenennen.
 *
 * @param showIds die umzubenennenden Serien; {@code null} heißt alle der Quelle
 */
public record TvRenameRequest(@NotNull @Valid TvRenamePatterns patterns, List<Long> showIds) {

    public TvRenameRequest {
        showIds = showIds == null ? null : List.copyOf(showIds);
    }
}
