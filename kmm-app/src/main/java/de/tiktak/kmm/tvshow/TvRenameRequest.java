package de.tiktak.kmm.tvshow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request to rename TV shows of a source.
 *
 * @param showIds the TV shows to rename; {@code null} means all of the source
 */
public record TvRenameRequest(@NotNull @Valid TvRenamePatterns patterns, List<Long> showIds) {

    public TvRenameRequest {
        showIds = showIds == null ? null : List.copyOf(showIds);
    }
}
